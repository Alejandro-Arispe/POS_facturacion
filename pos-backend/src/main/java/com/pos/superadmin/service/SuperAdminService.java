package com.pos.superadmin.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.auditoria.service.BitacoraService;
import com.pos.common.exception.ApiException;
import com.pos.common.security.SecurityUtils;
import com.pos.empresas.domain.Empresa;
import com.pos.empresas.domain.HistorialSuscripcion;
import com.pos.empresas.domain.Plan;
import com.pos.empresas.domain.PlanModulo;
import com.pos.empresas.repo.EmpresaRepository;
import com.pos.empresas.repo.HistorialSuscripcionRepository;
import com.pos.empresas.repo.PlanModuloRepository;
import com.pos.empresas.repo.PlanRepository;
import com.pos.empresas.repo.SucursalRepository;
import com.pos.superadmin.dto.SuperAdminDtos;
import com.pos.usuarios.domain.Modulo;
import com.pos.usuarios.domain.Usuario;
import com.pos.usuarios.repo.ModuloRepository;
import com.pos.usuarios.repo.RolRepository;
import com.pos.usuarios.repo.UsuarioRepository;
import com.pos.usuarios.repo.UsuarioRolRepository;

@Service
public class SuperAdminService {

	private static final String ESTADO_ACTIVO = "activo";
	private static final String ESTADO_REEMPLAZADO = "reemplazado";

	private final EmpresaRepository empresaRepository;
	private final SucursalRepository sucursalRepository;
	private final HistorialSuscripcionRepository historialRepository;
	private final PlanRepository planRepository;
	private final PlanModuloRepository planModuloRepository;
	private final ModuloRepository moduloRepository;
	private final UsuarioRepository usuarioRepository;
	private final UsuarioRolRepository usuarioRolRepository;
	private final RolRepository rolRepository;
	private final BitacoraService bitacoraService;

	public SuperAdminService(
			EmpresaRepository empresaRepository,
			SucursalRepository sucursalRepository,
			HistorialSuscripcionRepository historialRepository,
			PlanRepository planRepository,
			PlanModuloRepository planModuloRepository,
			ModuloRepository moduloRepository,
			UsuarioRepository usuarioRepository,
			UsuarioRolRepository usuarioRolRepository,
			RolRepository rolRepository,
			BitacoraService bitacoraService) {
		this.empresaRepository = empresaRepository;
		this.sucursalRepository = sucursalRepository;
		this.historialRepository = historialRepository;
		this.planRepository = planRepository;
		this.planModuloRepository = planModuloRepository;
		this.moduloRepository = moduloRepository;
		this.usuarioRepository = usuarioRepository;
		this.usuarioRolRepository = usuarioRolRepository;
		this.rolRepository = rolRepository;
		this.bitacoraService = bitacoraService;
	}

	/** Lanza 403 si el usuario autenticado no es SuperAdmin. */
	public Usuario requireSuperAdmin() {
		Usuario usuario = SecurityUtils.currentUser();
		if (!usuario.esSuperAdmin()) {
			throw ApiException.forbidden("Acceso exclusivo del SuperAdmin del sistema.");
		}
		return usuario;
	}

	@Transactional(readOnly = true)
	public SuperAdminDtos.Resumen resumen() {
		requireSuperAdmin();
		List<Empresa> empresas = empresaRepository.findAll();
		long conPlan = 0;
		BigDecimal ingresos = BigDecimal.ZERO;
		for (Empresa empresa : empresas) {
			HistorialSuscripcion activa = suscripcionActiva(empresa.getIdEmpresa());
			if (activa != null) {
				conPlan++;
				if (activa.getPlan() != null && activa.getPlan().getPrecio() != null) {
					ingresos = ingresos.add(activa.getPlan().getPrecio());
				}
			}
		}
		List<Usuario> usuarios = usuarioRepository.findAll();
		Map<String, Long> bitacora = bitacoraService.resumenHoy();
		return new SuperAdminDtos.Resumen(
				empresas.size(),
				empresas.stream().filter(e -> Boolean.TRUE.equals(e.getActivo())).count(),
				conPlan,
				usuarios.size(),
				usuarios.stream().filter(u -> Boolean.TRUE.equals(u.getActivo())).count(),
				ingresos,
				bitacora.get("hoy"),
				bitacora.get("total"));
	}

	@Transactional(readOnly = true)
	public List<SuperAdminDtos.EmpresaInfo> empresas() {
		requireSuperAdmin();
		Integer idRolAdmin = rolRepository.findByNombreIgnoreCase("ADMINISTRADOR")
				.map(r -> r.getIdRol())
				.orElse(null);
		return empresaRepository.findAll().stream()
				.sorted(Comparator.comparing(Empresa::getIdEmpresa))
				.map(empresa -> {
					List<String> admins = idRolAdmin == null ? List.of()
							: usuarioRolRepository.findByIdEmpresaAndRol_IdRolAndActivoTrue(empresa.getIdEmpresa(), idRolAdmin)
									.stream()
									.map(ur -> ur.getUsuario().getEmail())
									.distinct()
									.toList();
					return new SuperAdminDtos.EmpresaInfo(
							empresa.getIdEmpresa(),
							empresa.getNombre(),
							empresa.getRazonSocial(),
							empresa.getNit(),
							empresa.getCorreo(),
							empresa.getFechaCreacion(),
							empresa.getActivo(),
							admins,
							sucursalRepository.countByEmpresa_IdEmpresaAndActivoTrue(empresa.getIdEmpresa()),
							toSuscripcion(suscripcionActiva(empresa.getIdEmpresa())));
				})
				.toList();
	}

	@Transactional
	public SuperAdminDtos.EmpresaInfo cambiarEstadoEmpresa(Integer idEmpresa, SuperAdminDtos.CambiarEstado datos) {
		requireSuperAdmin();
		if (datos == null || datos.activo() == null) {
			throw ApiException.badRequest("Debe indicar el estado 'activo'.");
		}
		Empresa empresa = requireEmpresa(idEmpresa);
		empresa.setActivo(datos.activo());
		empresaRepository.save(empresa);
		return buscarEmpresa(idEmpresa);
	}

	/** Asigna un plan manualmente (por ejemplo, tras un pago en efectivo o una cortesia). */
	@Transactional
	public SuperAdminDtos.EmpresaInfo asignarSuscripcion(Integer idEmpresa, SuperAdminDtos.AsignarSuscripcion datos) {
		requireSuperAdmin();
		if (datos == null || datos.idPlan() == null) {
			throw ApiException.badRequest("Debe indicar el plan.");
		}
		int meses = datos.meses() == null || datos.meses() <= 0 ? 1 : Math.min(datos.meses(), 36);
		Empresa empresa = requireEmpresa(idEmpresa);
		Plan plan = planRepository.findById(datos.idPlan())
				.orElseThrow(() -> ApiException.notFound("Plan no encontrado."));

		for (HistorialSuscripcion anterior : historialRepository.findActivasPorEmpresa(idEmpresa, LocalDate.now())) {
			anterior.setEstado(ESTADO_REEMPLAZADO);
			anterior.setFechaFin(LocalDate.now().minusDays(1));
			historialRepository.save(anterior);
		}

		HistorialSuscripcion nueva = new HistorialSuscripcion();
		nueva.setEmpresa(empresa);
		nueva.setPlan(plan);
		nueva.setFechaInicio(LocalDate.now());
		nueva.setFechaFin(LocalDate.now().plusMonths(meses));
		nueva.setEstado(ESTADO_ACTIVO);
		nueva.setStripePaymentStatus("asignado_superadmin");
		historialRepository.save(nueva);
		return buscarEmpresa(idEmpresa);
	}

	@Transactional
	public SuperAdminDtos.EmpresaInfo cancelarSuscripcion(Integer idEmpresa) {
		requireSuperAdmin();
		requireEmpresa(idEmpresa);
		for (HistorialSuscripcion activa : historialRepository.findActivasPorEmpresa(idEmpresa, LocalDate.now())) {
			activa.setEstado("cancelado");
			activa.setFechaFin(LocalDate.now().minusDays(1));
			historialRepository.save(activa);
		}
		return buscarEmpresa(idEmpresa);
	}

	@Transactional(readOnly = true)
	public List<SuperAdminDtos.PlanInfo> planes() {
		requireSuperAdmin();
		Map<Integer, Modulo> modulos = moduloRepository.findAll().stream()
				.collect(Collectors.toMap(Modulo::getIdModulo, Function.identity()));
		Map<Integer, Long> suscritas = new java.util.HashMap<>();
		for (Empresa empresa : empresaRepository.findAll()) {
			HistorialSuscripcion activa = suscripcionActiva(empresa.getIdEmpresa());
			if (activa != null && activa.getPlan() != null) {
				suscritas.merge(activa.getPlan().getIdPlan(), 1L, Long::sum);
			}
		}
		return planRepository.findAllByOrderByIdPlanAsc().stream()
				.map(plan -> toPlan(plan, modulos, suscritas.getOrDefault(plan.getIdPlan(), 0L)))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<SuperAdminDtos.ModuloInfo> modulos() {
		requireSuperAdmin();
		return moduloRepository.findAllByOrderByIdModuloAsc().stream()
				.map(m -> new SuperAdminDtos.ModuloInfo(m.getIdModulo(), m.getCodigo(), m.getNombre()))
				.toList();
	}

	@Transactional
	public SuperAdminDtos.PlanInfo crearPlan(SuperAdminDtos.PlanGuardar datos) {
		requireSuperAdmin();
		validarPlan(datos);
		if (planRepository.findByNombreIgnoreCase(datos.nombre().trim()).isPresent()) {
			throw ApiException.badRequest("Ya existe un plan con ese nombre.");
		}
		Plan plan = new Plan();
		aplicarPlan(plan, datos);
		plan = planRepository.save(plan);
		sincronizarModulos(plan, datos);
		return planPorId(plan.getIdPlan());
	}

	@Transactional
	public SuperAdminDtos.PlanInfo actualizarPlan(Integer idPlan, SuperAdminDtos.PlanGuardar datos) {
		requireSuperAdmin();
		validarPlan(datos);
		Plan plan = planRepository.findById(idPlan)
				.orElseThrow(() -> ApiException.notFound("Plan no encontrado."));
		planRepository.findByNombreIgnoreCase(datos.nombre().trim())
				.filter(otro -> !otro.getIdPlan().equals(idPlan))
				.ifPresent(otro -> {
					throw ApiException.badRequest("Ya existe un plan con ese nombre.");
				});
		aplicarPlan(plan, datos);
		planRepository.save(plan);
		sincronizarModulos(plan, datos);
		return planPorId(idPlan);
	}

	@Transactional(readOnly = true)
	public List<SuperAdminDtos.UsuarioInfo> usuarios() {
		requireSuperAdmin();
		Map<Integer, String> nombresEmpresa = empresaRepository.findAll().stream()
				.collect(Collectors.toMap(Empresa::getIdEmpresa, Empresa::getNombre));
		return usuarioRepository.findAll().stream()
				.sorted(Comparator.comparing(Usuario::getIdUsuario))
				.map(u -> {
					Set<String> empresas = new LinkedHashSet<>();
					usuarioRolRepository.findByUsuario_IdUsuarioAndActivoTrue(u.getIdUsuario())
							.forEach(ur -> empresas.add(nombresEmpresa.getOrDefault(ur.getIdEmpresa(), "#" + ur.getIdEmpresa())));
					return new SuperAdminDtos.UsuarioInfo(
							u.getIdUsuario(),
							u.getEmail(),
							u.getPersona() != null ? u.getPersona().getNombreCompleto() : null,
							u.getFechaCreacion(),
							u.getActivo(),
							u.esSuperAdmin(),
							new ArrayList<>(empresas));
				})
				.toList();
	}

	@Transactional
	public SuperAdminDtos.UsuarioInfo cambiarEstadoUsuario(Integer idUsuario, SuperAdminDtos.CambiarEstado datos) {
		Usuario actual = requireSuperAdmin();
		if (datos == null || datos.activo() == null) {
			throw ApiException.badRequest("Debe indicar el estado 'activo'.");
		}
		if (actual.getIdUsuario().equals(idUsuario) && !datos.activo()) {
			throw ApiException.badRequest("No puedes desactivar tu propia cuenta de SuperAdmin.");
		}
		Usuario usuario = usuarioRepository.findById(idUsuario)
				.orElseThrow(() -> ApiException.notFound("Usuario no encontrado."));
		usuario.setActivo(datos.activo());
		usuarioRepository.save(usuario);
		return usuarios().stream()
				.filter(u -> u.idUsuario().equals(idUsuario))
				.findFirst()
				.orElseThrow();
	}

	private SuperAdminDtos.EmpresaInfo buscarEmpresa(Integer idEmpresa) {
		return empresas().stream()
				.filter(e -> e.idEmpresa().equals(idEmpresa))
				.findFirst()
				.orElseThrow(() -> ApiException.notFound("Empresa no encontrada."));
	}

	private SuperAdminDtos.PlanInfo planPorId(Integer idPlan) {
		return planes().stream()
				.filter(p -> p.idPlan().equals(idPlan))
				.findFirst()
				.orElseThrow(() -> ApiException.notFound("Plan no encontrado."));
	}

	private Empresa requireEmpresa(Integer idEmpresa) {
		return empresaRepository.findById(idEmpresa)
				.orElseThrow(() -> ApiException.notFound("Empresa no encontrada."));
	}

	private HistorialSuscripcion suscripcionActiva(Integer idEmpresa) {
		List<HistorialSuscripcion> activas = historialRepository.findActivasPorEmpresa(idEmpresa, LocalDate.now());
		return activas.isEmpty() ? null : activas.get(0);
	}

	private SuperAdminDtos.SuscripcionInfo toSuscripcion(HistorialSuscripcion hs) {
		if (hs == null) {
			return null;
		}
		return new SuperAdminDtos.SuscripcionInfo(
				hs.getIdHistorialSuscripcion(),
				hs.getPlan() != null ? hs.getPlan().getIdPlan() : null,
				hs.getPlan() != null ? hs.getPlan().getNombre() : null,
				hs.getEstado(),
				hs.getFechaInicio(),
				hs.getFechaFin());
	}

	private SuperAdminDtos.PlanInfo toPlan(Plan plan, Map<Integer, Modulo> modulos, long suscritas) {
		List<SuperAdminDtos.PlanModuloInfo> items = planModuloRepository.findByPlan_IdPlan(plan.getIdPlan()).stream()
				.sorted(Comparator.comparing(PlanModulo::getIdModulo))
				.map(pm -> {
					Modulo m = modulos.get(pm.getIdModulo());
					return new SuperAdminDtos.PlanModuloInfo(
							pm.getIdModulo(),
							m != null ? m.getCodigo() : null,
							m != null ? m.getNombre() : null,
							pm.getConfiguracion());
				})
				.toList();
		return new SuperAdminDtos.PlanInfo(
				plan.getIdPlan(), plan.getNombre(), plan.getDescripcion(), plan.getPrecio(), items, suscritas);
	}

	private void validarPlan(SuperAdminDtos.PlanGuardar datos) {
		if (datos == null || datos.nombre() == null || datos.nombre().isBlank()) {
			throw ApiException.badRequest("El nombre del plan es obligatorio.");
		}
		if (datos.precio() == null || datos.precio().compareTo(BigDecimal.ZERO) < 0) {
			throw ApiException.badRequest("El precio debe ser mayor o igual a cero.");
		}
		if (datos.modulos() == null || datos.modulos().isEmpty()) {
			throw ApiException.badRequest("El plan debe incluir al menos un modulo.");
		}
	}

	private void aplicarPlan(Plan plan, SuperAdminDtos.PlanGuardar datos) {
		plan.setNombre(datos.nombre().trim());
		plan.setDescripcion(datos.descripcion() == null || datos.descripcion().isBlank()
				? datos.nombre().trim()
				: datos.descripcion().trim());
		plan.setPrecio(datos.precio());
	}

	private void sincronizarModulos(Plan plan, SuperAdminDtos.PlanGuardar datos) {
		Set<Integer> solicitados = new LinkedHashSet<>();
		for (String codigo : datos.modulos()) {
			Modulo modulo = moduloRepository.findByCodigoIgnoreCase(codigo)
					.orElseThrow(() -> ApiException.badRequest("Modulo desconocido: " + codigo));
			solicitados.add(modulo.getIdModulo());
		}
		List<PlanModulo> actuales = planModuloRepository.findByPlan_IdPlan(plan.getIdPlan());
		for (PlanModulo pm : actuales) {
			if (!solicitados.contains(pm.getIdModulo())) {
				planModuloRepository.delete(pm);
			}
		}
		for (Integer idModulo : solicitados) {
			PlanModulo pm = planModuloRepository.findByPlan_IdPlanAndIdModulo(plan.getIdPlan(), idModulo)
					.orElseGet(() -> {
						PlanModulo nuevo = new PlanModulo();
						nuevo.setPlan(plan);
						nuevo.setIdModulo(idModulo);
						return nuevo;
					});
			if (datos.configuracion() != null && !datos.configuracion().isBlank()) {
				pm.setConfiguracion(datos.configuracion());
			}
			planModuloRepository.save(pm);
		}
	}
}
