package com.pos.empresas.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;
import com.pos.common.security.SecurityUtils;
import com.pos.empresas.domain.Empresa;
import com.pos.empresas.domain.HistorialSuscripcion;
import com.pos.empresas.domain.Plan;
import com.pos.empresas.domain.PlanModulo;
import com.pos.empresas.dto.EmpresaCreadaResponse;
import com.pos.empresas.dto.EmpresaCreate;
import com.pos.empresas.dto.EmpresaResponse;
import com.pos.empresas.dto.EmpresaUpdate;
import com.pos.empresas.dto.MisPermisosEmpresaResponse;
import com.pos.empresas.dto.ModuloResponse;
import com.pos.empresas.dto.PermisoConRolResponse;
import com.pos.empresas.dto.PermisoSimpleResponse;
import com.pos.empresas.dto.PermisosPorModuloResponse;
import com.pos.empresas.dto.PlanConModulosResponse;
import com.pos.empresas.dto.PlanModuloResponse;
import com.pos.empresas.dto.SuscripcionActivaDetalleResponse;
import com.pos.empresas.dto.SuscripcionActivaResumenResponse;
import com.pos.empresas.dto.UsuarioRolResumenResponse;
import com.pos.empresas.repo.EmpresaRepository;
import com.pos.empresas.repo.HistorialSuscripcionRepository;
import com.pos.empresas.repo.PlanModuloRepository;
import com.pos.usuarios.domain.Modulo;
import com.pos.usuarios.domain.Permiso;
import com.pos.usuarios.domain.Rol;
import com.pos.usuarios.domain.RolPermiso;
import com.pos.usuarios.domain.Usuario;
import com.pos.usuarios.domain.UsuarioRol;
import com.pos.usuarios.repo.ModuloRepository;
import com.pos.usuarios.repo.PermisoRepository;
import com.pos.usuarios.repo.RolPermisoRepository;
import com.pos.usuarios.repo.RolRepository;
import com.pos.usuarios.repo.UsuarioRolRepository;

@Service
public class EmpresaService {

	private final EmpresaAccess empresaAccess;
	private final EmpresaRepository empresaRepository;
	private final UsuarioRolRepository usuarioRolRepository;
	private final RolRepository rolRepository;
	private final HistorialSuscripcionRepository historialSuscripcionRepository;
	private final PlanModuloRepository planModuloRepository;
	private final ModuloRepository moduloRepository;
	private final PermisoRepository permisoRepository;
	private final RolPermisoRepository rolPermisoRepository;

	public EmpresaService(
			EmpresaAccess empresaAccess,
			EmpresaRepository empresaRepository,
			UsuarioRolRepository usuarioRolRepository,
			RolRepository rolRepository,
			HistorialSuscripcionRepository historialSuscripcionRepository,
			PlanModuloRepository planModuloRepository,
			ModuloRepository moduloRepository,
			PermisoRepository permisoRepository,
			RolPermisoRepository rolPermisoRepository) {
		this.empresaAccess = empresaAccess;
		this.empresaRepository = empresaRepository;
		this.usuarioRolRepository = usuarioRolRepository;
		this.rolRepository = rolRepository;
		this.historialSuscripcionRepository = historialSuscripcionRepository;
		this.planModuloRepository = planModuloRepository;
		this.moduloRepository = moduloRepository;
		this.permisoRepository = permisoRepository;
		this.rolPermisoRepository = rolPermisoRepository;
	}

	@Transactional
	public EmpresaCreadaResponse crear(EmpresaCreate datos) {
		Usuario usuario = SecurityUtils.currentUser();
		empresaAccess.requireUsuarioActivo(usuario);
		Rol administrador = rolRepository.findByNombreIgnoreCase("ADMINISTRADOR")
				.orElseThrow(() -> ApiException.badRequest("No existe el rol ADMINISTRADOR."));

		if (empresaRepository.findByNit(datos.nit()).isPresent()
				|| empresaRepository.findByCorreoIgnoreCase(datos.correo()).isPresent()) {
			throw ApiException.badRequest("Ya existe una empresa con ese NIT o correo.");
		}

		try {
			Empresa empresa = new Empresa();
			empresa.setNombre(datos.nombre());
			empresa.setRazonSocial(datos.razonSocial());
			empresa.setNit(datos.nit());
			empresa.setCorreo(datos.correo());
			empresa.setFechaCreacion(LocalDate.now());
			empresa.setActivo(Boolean.TRUE);
			empresa = empresaRepository.saveAndFlush(empresa);

			UsuarioRol usuarioRol = new UsuarioRol();
			usuarioRol.setUsuario(usuario);
			usuarioRol.setRol(administrador);
			usuarioRol.setIdEmpresa(empresa.getIdEmpresa());
			usuarioRol.setIdSucursal(null);
			usuarioRol.setFecha(LocalDate.now());
			usuarioRol.setActivo(Boolean.TRUE);
			usuarioRol = usuarioRolRepository.saveAndFlush(usuarioRol);

			return new EmpresaCreadaResponse(toResponse(empresa), toUsuarioRolResumen(usuarioRol));
		} catch (DataIntegrityViolationException ex) {
			throw ApiException.badRequest("Ya existe una empresa con ese NIT o correo.");
		}
	}

	@Transactional(readOnly = true)
	public List<EmpresaResponse> misEmpresas() {
		Usuario usuario = SecurityUtils.currentUser();
		empresaAccess.requireUsuarioActivo(usuario);
		Set<Integer> ids = new LinkedHashSet<>();
		for (UsuarioRol usuarioRol : usuarioRolRepository.findByUsuario_IdUsuarioAndActivoTrue(usuario.getIdUsuario())) {
			ids.add(usuarioRol.getIdEmpresa());
		}
		return toEmpresaResponses(ids);
	}

	@Transactional(readOnly = true)
	public List<EmpresaResponse> misEmpresasEmpleado() {
		Usuario usuario = SecurityUtils.currentUser();
		empresaAccess.requireUsuarioActivo(usuario);
		Rol empleado = rolRepository.findByNombreIgnoreCase("EMPLEADO")
				.orElseThrow(() -> ApiException.badRequest("No existe el rol EMPLEADO."));
		Set<Integer> ids = new LinkedHashSet<>();
		for (UsuarioRol usuarioRol : usuarioRolRepository.findByUsuario_IdUsuarioAndActivoTrue(usuario.getIdUsuario())) {
			if (usuarioRol.getRol() != null && empleado.getIdRol().equals(usuarioRol.getRol().getIdRol())) {
				ids.add(usuarioRol.getIdEmpresa());
			}
		}
		return toEmpresaResponses(ids);
	}

	@Transactional(readOnly = true)
	public EmpresaResponse obtener(Integer idEmpresa) {
		return toResponse(empresaAccess.requireEmpresa(SecurityUtils.currentUser(), idEmpresa));
	}

	@Transactional
	public EmpresaResponse actualizar(Integer idEmpresa, EmpresaUpdate datos) {
		Empresa empresa = empresaAccess.requireEmpresa(SecurityUtils.currentUser(), idEmpresa);
		empresa.setNombre(datos.nombre());
		empresa.setRazonSocial(datos.razonSocial());
		empresa.setNit(datos.nit());
		empresa.setCorreo(datos.correo());
		empresa.setActivo(datos.activo());
		return toResponse(empresaRepository.save(empresa));
	}

	@Transactional(readOnly = true)
	public MisPermisosEmpresaResponse misPermisos(Integer idEmpresa) {
		Usuario usuario = SecurityUtils.currentUser();
		empresaAccess.requireEmpresa(usuario, idEmpresa);

		List<UsuarioRol> roles = usuarioRolRepository.findActivosDistintoCliente(usuario.getIdUsuario(), idEmpresa);
		if (roles.isEmpty()) {
			throw ApiException.notFound("No se encontro un rol distinto de cliente para este usuario en esta empresa.");
		}
		UsuarioRol usuarioRol = roles.get(0);

		List<PermisoConRolResponse> permisos = new ArrayList<>();
		for (RolPermiso rolPermiso : rolPermisoRepository.findByRolIdWithPermisoAndModulo(usuarioRol.getRol().getIdRol())) {
			Permiso permiso = rolPermiso.getPermiso();
			Modulo modulo = permiso.getModulo();
			permisos.add(new PermisoConRolResponse(
					permiso.getIdPermiso(),
					permiso.getCodigo(),
					permiso.getNombre(),
					modulo != null ? modulo.getIdModulo() : null,
					toModulo(modulo),
					Boolean.TRUE.equals(rolPermiso.getActivo())));
		}

		return new MisPermisosEmpresaResponse(permisos, toSuscripcionDetalle(idEmpresa));
	}

	@Transactional(readOnly = true)
	public List<PermisosPorModuloResponse> permisosPorModulo() {
		empresaAccess.requireUsuarioActivo(SecurityUtils.currentUser());
		Map<Integer, PermisosPorModuloResponse> agrupados = new LinkedHashMap<>();
		for (Permiso permiso : permisoRepository.findAllWithModulo()) {
			Modulo modulo = permiso.getModulo();
			if (modulo == null) {
				continue;
			}
			PermisosPorModuloResponse actual = agrupados.computeIfAbsent(
					modulo.getIdModulo(),
					id -> new PermisosPorModuloResponse(
							modulo.getIdModulo(),
							modulo.getCodigo(),
							modulo.getNombre(),
							new ArrayList<>()));
			actual.permisos().add(new PermisoSimpleResponse(
					permiso.getIdPermiso(),
					permiso.getCodigo(),
					permiso.getNombre()));
		}
		List<PermisosPorModuloResponse> resultado = new ArrayList<>(agrupados.values());
		resultado.sort(Comparator.comparing(PermisosPorModuloResponse::idModulo));
		return resultado;
	}

	private List<EmpresaResponse> toEmpresaResponses(Set<Integer> ids) {
		Map<Integer, Empresa> porId = new LinkedHashMap<>();
		for (Empresa empresa : empresaRepository.findAllById(ids)) {
			porId.put(empresa.getIdEmpresa(), empresa);
		}
		List<EmpresaResponse> resultado = new ArrayList<>();
		for (Integer id : ids) {
			Empresa empresa = porId.get(id);
			if (empresa != null) {
				resultado.add(toResponse(empresa));
			}
		}
		return resultado;
	}

	private EmpresaResponse toResponse(Empresa empresa) {
		return new EmpresaResponse(
				empresa.getIdEmpresa(),
				empresa.getNombre(),
				empresa.getRazonSocial(),
				empresa.getNit(),
				empresa.getCorreo(),
				empresa.getFechaCreacion(),
				empresa.getActivo(),
				toSuscripcionResumen(empresa.getIdEmpresa()));
	}

	private UsuarioRolResumenResponse toUsuarioRolResumen(UsuarioRol usuarioRol) {
		return new UsuarioRolResumenResponse(
				usuarioRol.getIdUsuarioRol(),
				usuarioRol.getUsuario() != null ? usuarioRol.getUsuario().getIdUsuario() : null,
				usuarioRol.getRol() != null ? usuarioRol.getRol().getIdRol() : null,
				usuarioRol.getIdEmpresa(),
				usuarioRol.getIdSucursal(),
				usuarioRol.getActivo());
	}

	private SuscripcionActivaResumenResponse toSuscripcionResumen(Integer idEmpresa) {
		List<HistorialSuscripcion> activas = historialSuscripcionRepository.findActivasPorEmpresa(
				idEmpresa, LocalDate.now());
		if (activas.isEmpty()) {
			return null;
		}
		HistorialSuscripcion suscripcion = activas.get(0);
		String planNombre = suscripcion.getPlan() != null ? suscripcion.getPlan().getNombre() : "Desconocido";
		return new SuscripcionActivaResumenResponse(suscripcion.getEstado(), suscripcion.getFechaFin(), planNombre);
	}

	private SuscripcionActivaDetalleResponse toSuscripcionDetalle(Integer idEmpresa) {
		List<HistorialSuscripcion> activas = historialSuscripcionRepository.findActivasPorEmpresa(
				idEmpresa, LocalDate.now());
		if (activas.isEmpty()) {
			return null;
		}
		HistorialSuscripcion suscripcion = activas.get(0);
		Plan plan = suscripcion.getPlan();
		if (plan == null) {
			return null;
		}
		List<PlanModulo> planModulos = new ArrayList<>(planModuloRepository.findByPlan_IdPlan(plan.getIdPlan()));
		planModulos.sort(Comparator.comparing(PlanModulo::getIdPlanModulo));
		List<PlanModuloResponse> planModuloResponses = new ArrayList<>();
		for (PlanModulo planModulo : planModulos) {
			Modulo modulo = moduloRepository.findById(planModulo.getIdModulo()).orElse(null);
			planModuloResponses.add(new PlanModuloResponse(
					planModulo.getIdPlanModulo(),
					plan.getIdPlan(),
					planModulo.getIdModulo(),
					planModulo.getConfiguracion(),
					toModulo(modulo)));
		}
		return new SuscripcionActivaDetalleResponse(
				suscripcion.getIdHistorialSuscripcion(),
				idEmpresa,
				plan.getIdPlan(),
				suscripcion.getFechaInicio() != null ? suscripcion.getFechaInicio().toString() : null,
				suscripcion.getFechaFin() != null ? suscripcion.getFechaFin().toString() : null,
				suscripcion.getEstado(),
				new PlanConModulosResponse(
						plan.getIdPlan(),
						plan.getNombre(),
						plan.getDescripcion(),
						plan.getPrecio(),
						planModuloResponses));
	}

	private ModuloResponse toModulo(Modulo modulo) {
		if (modulo == null) {
			return null;
		}
		return new ModuloResponse(modulo.getIdModulo(), modulo.getCodigo(), modulo.getNombre());
	}
}
