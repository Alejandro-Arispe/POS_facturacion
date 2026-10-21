package com.pos.empresas.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.pos.clientes.domain.Cliente;
import com.pos.clientes.repo.ClienteRepository;
import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;
import com.pos.common.mail.EmailService;
import com.pos.common.security.JwtService;
import com.pos.common.security.SecurityUtils;
import com.pos.empresas.domain.Empresa;
import com.pos.empresas.domain.HistorialSuscripcion;
import com.pos.empresas.domain.PlanModulo;
import com.pos.empresas.domain.Sucursal;
import com.pos.empresas.dto.ClienteEmpresaResponse;
import com.pos.empresas.dto.ClienteResponse;
import com.pos.empresas.dto.EditarPersonalCreate;
import com.pos.empresas.dto.EmpresaEmpleadoResponse;
import com.pos.empresas.dto.InvitacionAceptadaResult;
import com.pos.empresas.dto.InvitacionClienteCreate;
import com.pos.empresas.dto.InvitacionEmpleadoCreate;
import com.pos.empresas.dto.InvitacionEnviadaResponse;
import com.pos.empresas.dto.PersonaEmpleadoResponse;
import com.pos.empresas.dto.PersonalEmpresaAgrupadoResponse;
import com.pos.empresas.dto.PersonalEmpresaResponse;
import com.pos.empresas.dto.PersonalRelacionResponse;
import com.pos.empresas.dto.SucursalCreate;
import com.pos.empresas.dto.SucursalEmpleadoAsignadaResponse;
import com.pos.empresas.dto.SucursalResponse;
import java.math.BigDecimal;
import com.pos.empresas.dto.SucursalUpdate;
import com.pos.ventas.repo.CuentaPorCobrarRepository;
import com.pos.empresas.dto.UsuarioEmpleadoResponse;
import com.pos.empresas.repo.EmpresaRepository;
import com.pos.empresas.repo.HistorialSuscripcionRepository;
import com.pos.empresas.repo.PlanModuloRepository;
import com.pos.empresas.repo.SucursalRepository;
import com.pos.inventario.service.InventarioStockSync;
import com.pos.usuarios.domain.Persona;
import com.pos.usuarios.domain.Rol;
import com.pos.usuarios.domain.Usuario;
import com.pos.usuarios.domain.UsuarioRol;
import com.pos.usuarios.repo.RolRepository;
import com.pos.usuarios.repo.UsuarioRepository;
import com.pos.usuarios.repo.UsuarioRolRepository;

import io.jsonwebtoken.Claims;

@Service
public class SucursalService {

	private static final String LIMITE_ILIMITADO = "sin limite";
	private static final SecureRandom RANDOM = new SecureRandom();

	private final EmpresaAccess empresaAccess;
	private final SucursalRepository sucursalRepository;
	private final EmpresaRepository empresaRepository;
	private final UsuarioRolRepository usuarioRolRepository;
	private final UsuarioRepository usuarioRepository;
	private final RolRepository rolRepository;
	private final HistorialSuscripcionRepository historialSuscripcionRepository;
	private final PlanModuloRepository planModuloRepository;
	private final ClienteRepository clienteRepository;
	private final CuentaPorCobrarRepository cuentaPorCobrarRepository;
	private final InventarioStockSync inventarioStockSync;
	private final JwtService jwtService;
	private final EmailService emailService;
	private final ObjectMapper objectMapper;
	private final long invitationExpirationHours;
	private final String appBaseUrl;

	public SucursalService(
			EmpresaAccess empresaAccess,
			SucursalRepository sucursalRepository,
			EmpresaRepository empresaRepository,
			UsuarioRolRepository usuarioRolRepository,
			UsuarioRepository usuarioRepository,
			RolRepository rolRepository,
			HistorialSuscripcionRepository historialSuscripcionRepository,
			PlanModuloRepository planModuloRepository,
			ClienteRepository clienteRepository,
			CuentaPorCobrarRepository cuentaPorCobrarRepository,
			InventarioStockSync inventarioStockSync,
			JwtService jwtService,
			EmailService emailService,
			ObjectMapper objectMapper,
			@Value("${app.invitation.expiration-hours:72}") long invitationExpirationHours,
			@Value("${app.base-url:http://localhost:8000}") String appBaseUrl) {
		this.empresaAccess = empresaAccess;
		this.sucursalRepository = sucursalRepository;
		this.empresaRepository = empresaRepository;
		this.usuarioRolRepository = usuarioRolRepository;
		this.usuarioRepository = usuarioRepository;
		this.rolRepository = rolRepository;
		this.historialSuscripcionRepository = historialSuscripcionRepository;
		this.planModuloRepository = planModuloRepository;
		this.clienteRepository = clienteRepository;
		this.cuentaPorCobrarRepository = cuentaPorCobrarRepository;
		this.inventarioStockSync = inventarioStockSync;
		this.jwtService = jwtService;
		this.emailService = emailService;
		this.objectMapper = objectMapper;
		this.invitationExpirationHours = invitationExpirationHours;
		this.appBaseUrl = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
	}

	@Transactional
	public SucursalResponse crear(Integer idEmpresa, SucursalCreate datos) {
		Usuario usuario = SecurityUtils.currentUser();
		empresaAccess.requireEmpresa(usuario, idEmpresa);
		validarLimiteSucursales(idEmpresa);

		try {
			Sucursal sucursal = new Sucursal();
			sucursal.setEmpresa(empresaRepository.getReferenceById(idEmpresa));
			sucursal.setNombre(datos.nombre());
			sucursal.setDireccion(datos.direccion());
			sucursal.setTelefono(datos.telefono());
			sucursal.setCiudad(datos.ciudad());
			sucursal.setFechaRegistro(LocalDate.now());
			sucursal.setActivo(Boolean.TRUE);
			sucursal = sucursalRepository.saveAndFlush(sucursal);

			UsuarioRol sinSucursal = usuarioRolRepository
					.findFirstByUsuario_IdUsuarioAndIdEmpresaAndActivoTrueAndIdSucursalIsNull(
							usuario.getIdUsuario(), idEmpresa)
					.orElse(null);
			if (sinSucursal != null) {
				sinSucursal.setIdSucursal(sucursal.getIdSucursal());
				usuarioRolRepository.save(sinSucursal);
			} else {
				UsuarioRol base = usuarioRolRepository
						.findFirstByUsuario_IdUsuarioAndIdEmpresaAndActivoTrue(usuario.getIdUsuario(), idEmpresa)
						.orElseThrow(() -> ApiException.notFound("Empresa no encontrada para este usuario."));
				crearUsuarioRol(usuario, base.getRol(), idEmpresa, sucursal.getIdSucursal(), true);
			}

			inventarioStockSync.sincronizarStocksPorSucursal(sucursal.getIdSucursal(), LocalDateTime.now());
			return toResponse(sucursal);
		} catch (DataIntegrityViolationException ex) {
			throw ApiException.badRequest("No se pudo asignar la sucursal al usuario.");
		}
	}

	@Transactional(readOnly = true)
	public List<SucursalResponse> listar(Integer idEmpresa) {
		Usuario usuario = SecurityUtils.currentUser();
		empresaAccess.requireEmpresa(usuario, idEmpresa);
		return sucursalRepository.findAsignadasAUsuario(idEmpresa, usuario.getIdUsuario()).stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public SucursalResponse obtener(Integer idEmpresa, Integer idSucursal) {
		empresaAccess.requireEmpresa(SecurityUtils.currentUser(), idEmpresa);
		Sucursal sucursal = sucursalRepository.findByIdSucursalAndEmpresa_IdEmpresa(idSucursal, idEmpresa)
				.orElseThrow(() -> ApiException.notFound("Sucursal no encontrada para esta empresa."));
		return toResponse(sucursal);
	}

	@Transactional
	public SucursalResponse actualizar(Integer idSucursal, SucursalUpdate datos) {
		Usuario usuario = SecurityUtils.currentUser();
		Sucursal sucursal = sucursalRepository.findById(idSucursal)
				.orElseThrow(() -> ApiException.notFound("Sucursal no encontrada."));
		empresaAccess.requireEmpresa(usuario, sucursal.getEmpresa().getIdEmpresa());

		if (!Boolean.TRUE.equals(sucursal.getActivo()) && Boolean.TRUE.equals(datos.activo())) {
			validarLimiteSucursales(sucursal.getEmpresa().getIdEmpresa());
		}

		sucursal.setNombre(datos.nombre());
		sucursal.setDireccion(datos.direccion());
		sucursal.setTelefono(datos.telefono());
		sucursal.setCiudad(datos.ciudad());
		sucursal.setActivo(datos.activo());
		return toResponse(sucursalRepository.save(sucursal));
	}

	@Transactional(readOnly = true)
	public InvitacionEnviadaResponse invitarEmpleado(Integer idEmpresa, InvitacionEmpleadoCreate datos) {
		empresaAccess.requireEmpresa(SecurityUtils.currentUser(), idEmpresa);

		List<Integer> idSucursalesUnicas = new ArrayList<>(new LinkedHashSet<>(datos.idSucursales()));
		if (idSucursalesUnicas.size() != datos.idSucursales().size()) {
			throw ApiException.badRequest("No se permiten sucursales repetidas en la invitacion.");
		}

		List<Sucursal> sucursales = new ArrayList<>();
		for (Integer idSucursal : idSucursalesUnicas) {
			sucursales.add(requireSucursalDeEmpresa(idEmpresa, idSucursal));
		}

		Usuario invitado = usuarioRepository.findByEmailIgnoreCase(datos.email())
				.orElseThrow(() -> ApiException.notFound("No existe un usuario con ese correo."));
		Rol rol = requireRolActivo(datos.idRol(), idEmpresa);
		if (!"CLIENTE".equals(rol.getNombre())) {
			validarLimiteUsuarios(idEmpresa, invitado.getIdUsuario());
		}

		String token = jwtService.createInvitationToken(
				idEmpresa, invitado.getIdUsuario(), datos.idRol(), idSucursalesUnicas, invitationExpirationHours);
		String invitationLink = appBaseUrl + "/api/invitaciones/empleado/aceptar/" + token;
		String nombre = nombreUsuario(invitado);
		emailService.sendHtml(
				invitado.getEmail(),
				"Invitacion a sucursal - POS System",
				htmlInvitacionEmpleado(nombre, invitationLink));

		return new InvitacionEnviadaResponse(
				"Invitacion enviada correctamente.",
				invitado.getEmail(),
				invitationLink,
				sucursales.stream().map(Sucursal::getIdSucursal).toList(),
				datos.idRol());
	}

	@Transactional(readOnly = true)
	public InvitacionEnviadaResponse invitarCliente(Integer idEmpresa, InvitacionClienteCreate datos) {
		empresaAccess.requireEmpresa(SecurityUtils.currentUser(), idEmpresa);
		Empresa empresa = empresaRepository.findById(idEmpresa)
				.orElseThrow(() -> ApiException.notFound("Empresa no encontrada."));

		Usuario invitado = usuarioRepository.findByEmailIgnoreCase(datos.email())
				.orElseThrow(() -> ApiException.notFound("No existe un usuario con ese correo."));
		Rol rolCliente = rolRepository.findByNombreIgnoreCase("CLIENTE")
				.orElseThrow(() -> ApiException.badRequest("No existe el rol CLIENTE."));

		boolean yaEsCliente = usuarioRolRepository
				.findFirstByUsuario_IdUsuarioAndIdEmpresaAndRol_IdRolAndActivoTrueAndIdSucursalIsNull(
						invitado.getIdUsuario(), idEmpresa, rolCliente.getIdRol())
				.isPresent();
		if (yaEsCliente) {
			throw ApiException.badRequest("El usuario ya es cliente de esta empresa.");
		}

		String invitationLink = appBaseUrl + "/api/invitaciones/cliente/aceptar/"
				+ idEmpresa + "/" + invitado.getIdUsuario();
		emailService.sendHtml(
				invitado.getEmail(),
				"Invitacion para ser cliente de " + empresa.getNombre() + " - POS System",
				htmlInvitacionCliente(nombreUsuario(invitado), empresa.getNombre(), invitationLink));

		return new InvitacionEnviadaResponse(
				"Invitacion enviada correctamente.",
				invitado.getEmail(),
				invitationLink,
				null,
				null);
	}

	@Transactional
	public InvitacionAceptadaResult aceptarInvitacionEmpleado(String token) {
		Claims claims;
		try {
			claims = jwtService.parse(token);
		} catch (Exception ex) {
			throw ApiException.badRequest("Invitacion invalida o expirada.");
		}
		if (!"invitacion_empleado".equals(claims.get("tipo", String.class))) {
			throw ApiException.badRequest("Invitacion invalida.");
		}

		Integer idEmpresa;
		Integer idUsuario;
		Integer idRol;
		List<Integer> idSucursales;
		try {
			idEmpresa = intClaim(claims, "id_empresa");
			idUsuario = intClaim(claims, "id_usuario");
			idRol = intClaim(claims, "id_rol");
			idSucursales = idSucursalesClaim(claims);
		} catch (ApiException ex) {
			throw ex;
		} catch (Exception ex) {
			throw ApiException.badRequest("Invitacion invalida.");
		}
		if (idSucursales.isEmpty()) {
			throw ApiException.badRequest("La invitacion no contiene sucursales.");
		}

		Usuario usuario = usuarioRepository.findById(idUsuario)
				.orElseThrow(() -> ApiException.notFound("Usuario no encontrado o inactivo."));
		if (!Boolean.TRUE.equals(usuario.getActivo())) {
			throw ApiException.notFound("Usuario no encontrado o inactivo.");
		}
		Empresa empresa = empresaRepository.findById(idEmpresa)
				.orElseThrow(() -> ApiException.notFound("Empresa no encontrada."));
		Rol rol = requireRolActivo(idRol, idEmpresa);
		if (!"CLIENTE".equals(rol.getNombre())) {
			validarLimiteUsuarios(idEmpresa, idUsuario);
		}

		List<Sucursal> sucursales = new ArrayList<>();
		for (Integer idSucursal : new LinkedHashSet<>(idSucursales)) {
			sucursales.add(requireSucursalDeEmpresa(idEmpresa, idSucursal));
		}

		try {
			for (Sucursal sucursal : sucursales) {
				boolean existe = usuarioRolRepository
						.findFirstByUsuario_IdUsuarioAndIdEmpresaAndIdSucursalAndRol_IdRolAndActivoTrue(
								idUsuario, idEmpresa, sucursal.getIdSucursal(), idRol)
						.isPresent();
				if (existe) {
					continue;
				}
				crearUsuarioRol(usuario, rol, idEmpresa, sucursal.getIdSucursal(), true);
			}
		} catch (DataIntegrityViolationException ex) {
			throw ApiException.badRequest("No se pudo aceptar la invitacion.");
		}

		return new InvitacionAceptadaResult("Invitacion aceptada correctamente.", empresa.getNombre());
	}

	@Transactional
	public InvitacionAceptadaResult aceptarInvitacionCliente(Integer idEmpresa, Integer idUsuario) {
		Usuario usuario = usuarioRepository.findById(idUsuario)
				.orElseThrow(() -> ApiException.notFound("Usuario no encontrado o inactivo."));
		if (!Boolean.TRUE.equals(usuario.getActivo())) {
			throw ApiException.notFound("Usuario no encontrado o inactivo.");
		}
		Empresa empresa = empresaRepository.findById(idEmpresa)
				.orElseThrow(() -> ApiException.notFound("Empresa no encontrada."));
		Rol rolCliente = rolRepository.findByNombreIgnoreCase("CLIENTE")
				.orElseThrow(() -> ApiException.badRequest("No existe el rol CLIENTE."));

		UsuarioRol existente = usuarioRolRepository
				.findFirstByUsuario_IdUsuarioAndIdEmpresaAndRol_IdRolAndActivoTrueAndIdSucursalIsNull(
						idUsuario, idEmpresa, rolCliente.getIdRol())
				.orElse(null);
		if (existente != null) {
			return new InvitacionAceptadaResult("Ya eres cliente de " + empresa.getNombre() + ".", empresa.getNombre());
		}

		try {
			crearUsuarioRol(usuario, rolCliente, idEmpresa, null, true);
			Cliente cliente = new Cliente();
			cliente.setIdUsuario(idUsuario);
			cliente.setCategoriaCliente(null);
			cliente.setCodigoCliente(generarCodigoCliente());
			cliente.setSaldoCredito(null);
			cliente.setLimiteCredito(null);
			cliente.setActivo(Boolean.TRUE);
			clienteRepository.save(cliente);
		} catch (DataIntegrityViolationException ex) {
			throw ApiException.badRequest("No se pudo aceptar la invitacion.");
		}

		return new InvitacionAceptadaResult("Ahora eres cliente de " + empresa.getNombre() + ".", empresa.getNombre());
	}

	@Transactional(readOnly = true)
	public List<PersonalEmpresaAgrupadoResponse> obtenerPersonal(Integer idEmpresa) {
		empresaAccess.requireEmpresa(SecurityUtils.currentUser(), idEmpresa);
		List<UsuarioRol> usuariosRol = usuarioRolRepository.findPersonalExcluyendoRoles(
				idEmpresa, List.of("ADMINISTRADOR", "CLIENTE"));

		Map<Integer, PersonalEmpresaAgrupadoResponse> porUsuario = new LinkedHashMap<>();
		for (UsuarioRol usuarioRol : usuariosRol) {
			Integer idUsuario = usuarioRol.getUsuario().getIdUsuario();
			PersonalEmpresaAgrupadoResponse actual = porUsuario.computeIfAbsent(
					idUsuario,
					id -> new PersonalEmpresaAgrupadoResponse(id, toUsuarioEmpleado(usuarioRol.getUsuario()), new ArrayList<>()));
			actual.relaciones().add(new PersonalRelacionResponse(
					usuarioRol.getIdUsuarioRol(),
					usuarioRol.getRol().getIdRol(),
					usuarioRol.getIdEmpresa(),
					usuarioRol.getIdSucursal(),
					usuarioRol.getActivo()));
		}
		return new ArrayList<>(porUsuario.values());
	}

	@Transactional
	public List<PersonalEmpresaResponse> editarPersonal(Integer idEmpresa, EditarPersonalCreate datos) {
		empresaAccess.requireEmpresa(SecurityUtils.currentUser(), idEmpresa);

		List<Integer> idSucursalesUnicas = new ArrayList<>(new LinkedHashSet<>(datos.idSucursales()));
		if (idSucursalesUnicas.size() != datos.idSucursales().size()) {
			throw ApiException.badRequest("No se permiten sucursales repetidas.");
		}

		List<Sucursal> sucursales = new ArrayList<>();
		for (Integer idSucursal : idSucursalesUnicas) {
			sucursales.add(requireSucursalDeEmpresa(idEmpresa, idSucursal));
		}

		Usuario usuario = usuarioRepository.findByEmailIgnoreCase(datos.email())
				.orElseThrow(() -> ApiException.notFound("No existe un usuario con ese correo."));
		Rol rol = requireRolActivo(datos.idRol(), idEmpresa);

		List<UsuarioRol> usuariosRol = usuarioRolRepository.findByUsuario_IdUsuarioAndIdEmpresa(
				usuario.getIdUsuario(), idEmpresa);
		if (usuariosRol.isEmpty() && !Boolean.TRUE.equals(datos.activo())) {
			throw ApiException.notFound("No existe relacion del usuario con esta empresa.");
		}

		try {
			if (!Boolean.TRUE.equals(datos.activo())) {
				for (UsuarioRol usuarioRol : usuariosRol) {
					usuarioRol.setActivo(Boolean.FALSE);
				}
				usuarioRolRepository.saveAll(usuariosRol);
				return usuariosRol.stream().map(this::toPersonalEmpresa).toList();
			}

			boolean todasApagadas = !usuariosRol.isEmpty()
					&& usuariosRol.stream().allMatch(ur -> !Boolean.TRUE.equals(ur.getActivo()));
			if (todasApagadas) {
				for (UsuarioRol usuarioRol : usuariosRol) {
					usuarioRol.setActivo(Boolean.TRUE);
				}
			}

			Map<Integer, UsuarioRol> existentesPorSucursal = new HashMap<>();
			Map<String, UsuarioRol> existentesPorSucursalYRol = new HashMap<>();
			for (UsuarioRol usuarioRol : usuariosRol) {
				if (usuarioRol.getIdSucursal() == null) {
					continue;
				}
				existentesPorSucursal.putIfAbsent(usuarioRol.getIdSucursal(), usuarioRol);
				existentesPorSucursalYRol.put(usuarioRol.getIdSucursal() + ":" + usuarioRol.getRol().getIdRol(), usuarioRol);
			}

			Set<Integer> idsSolicitadas = new HashSet<>(idSucursalesUnicas);
			Set<Integer> relacionesSeleccionadas = new HashSet<>();

			for (Sucursal sucursal : sucursales) {
				UsuarioRol usuarioRol = existentesPorSucursalYRol.get(sucursal.getIdSucursal() + ":" + rol.getIdRol());
				if (usuarioRol == null) {
					usuarioRol = existentesPorSucursal.get(sucursal.getIdSucursal());
				}
				if (usuarioRol == null) {
					usuarioRol = crearUsuarioRol(usuario, rol, idEmpresa, sucursal.getIdSucursal(), true);
					usuariosRol.add(usuarioRol);
				} else {
					usuarioRol.setRol(rol);
					usuarioRol.setActivo(Boolean.TRUE);
				}
				relacionesSeleccionadas.add(usuarioRol.getIdUsuarioRol());
			}

			for (UsuarioRol usuarioRol : usuariosRol) {
				if (usuarioRol.getIdSucursal() == null) {
					continue;
				}
				if (!idsSolicitadas.contains(usuarioRol.getIdSucursal())
						|| !relacionesSeleccionadas.contains(usuarioRol.getIdUsuarioRol())) {
					usuarioRol.setActivo(Boolean.FALSE);
				}
			}

			usuarioRolRepository.saveAll(usuariosRol);
			return usuarioRolRepository.findByUsuario_IdUsuarioAndIdEmpresa(usuario.getIdUsuario(), idEmpresa).stream()
					.map(this::toPersonalEmpresa)
					.toList();
		} catch (DataIntegrityViolationException ex) {
			throw ApiException.badRequest("No se pudo editar el personal.");
		}
	}

	@Transactional(readOnly = true)
	public List<ClienteEmpresaResponse> obtenerClientes(Integer idEmpresa) {
		empresaAccess.requireEmpresa(SecurityUtils.currentUser(), idEmpresa);
		Rol rolCliente = rolRepository.findByNombreIgnoreCase("CLIENTE")
				.orElseThrow(() -> ApiException.badRequest("No existe el rol CLIENTE."));

		Map<Integer, BigDecimal> saldosMap = new HashMap<>();
		for (Object[] row : cuentaPorCobrarRepository.findSaldosPendientesPorCliente()) {
			if (row != null && row.length >= 2 && row[0] instanceof Integer idCli && row[1] instanceof BigDecimal sum) {
				saldosMap.put(idCli, sum);
			}
		}

		List<ClienteEmpresaResponse> resultado = new ArrayList<>();
		for (UsuarioRol usuarioRol : usuarioRolRepository.findActivosPorEmpresaYRolSinSucursal(
				idEmpresa, rolCliente.getIdRol())) {
			Cliente cliente = clienteRepository.findFirstByIdUsuarioOrderByIdClienteAsc(
					usuarioRol.getUsuario().getIdUsuario()).orElse(null);
			BigDecimal saldo = BigDecimal.ZERO;
			if (cliente != null) {
				saldo = saldosMap.getOrDefault(cliente.getIdCliente(),
						cliente.getSaldoCredito() != null ? cliente.getSaldoCredito() : BigDecimal.ZERO);
			}
			resultado.add(new ClienteEmpresaResponse(
					usuarioRol.getIdUsuarioRol(),
					usuarioRol.getUsuario().getIdUsuario(),
					usuarioRol.getRol().getIdRol(),
					usuarioRol.getIdEmpresa(),
					usuarioRol.getIdSucursal(),
					usuarioRol.getActivo(),
					toUsuarioEmpleado(usuarioRol.getUsuario()),
					toCliente(cliente, saldo)));
		}
		return resultado;
	}

	@Transactional(readOnly = true)
	public List<SucursalEmpleadoAsignadaResponse> misSucursalesEmpleado(Integer idEmpresa) {
		Usuario usuario = SecurityUtils.currentUser();
		if (idEmpresa != null) {
			empresaAccess.requireEmpresa(usuario, idEmpresa);
		}
		Rol empleado = rolRepository.findByNombreIgnoreCase("EMPLEADO")
				.orElseThrow(() -> ApiException.badRequest("No existe el rol EMPLEADO."));

		List<SucursalEmpleadoAsignadaResponse> resultado = new ArrayList<>();
		for (UsuarioRol usuarioRol : usuarioRolRepository.findSucursalesEmpleado(
				usuario.getIdUsuario(), empleado.getIdRol(), idEmpresa)) {
			Empresa empresa = empresaRepository.findById(usuarioRol.getIdEmpresa()).orElse(null);
			Sucursal sucursal = sucursalRepository.findById(usuarioRol.getIdSucursal()).orElse(null);
			resultado.add(new SucursalEmpleadoAsignadaResponse(
					usuarioRol.getIdUsuarioRol(),
					usuario.getIdUsuario(),
					usuarioRol.getRol().getIdRol(),
					usuarioRol.getIdEmpresa(),
					usuarioRol.getIdSucursal(),
					usuarioRol.getActivo(),
					toEmpresaEmpleado(empresa),
					sucursal != null ? toResponse(sucursal) : null));
		}
		return resultado;
	}

	private void validarLimiteSucursales(Integer idEmpresa) {
		Integer maxSucursales = obtenerLimitePlan(idEmpresa, "max_sucursales");
		if (maxSucursales == null) {
			return;
		}
		long actuales = sucursalRepository.countByEmpresa_IdEmpresaAndActivoTrue(idEmpresa);
		if (actuales >= maxSucursales) {
			throw ApiException.badRequest("La empresa alcanzo el limite de sucursales permitido por su plan.");
		}
	}

	private void validarLimiteUsuarios(Integer idEmpresa, Integer idUsuarioInvitado) {
		boolean yaTieneRol = !usuarioRolRepository.findActivosDistintoCliente(idUsuarioInvitado, idEmpresa).isEmpty();
		if (yaTieneRol) {
			return;
		}
		Integer maxUsuarios = obtenerLimitePlan(idEmpresa, "max_usuarios");
		if (maxUsuarios == null) {
			return;
		}
		long actuales = usuarioRolRepository.countUsuariosActivosExcluyendoRoles(idEmpresa, List.of("CLIENTE"));
		if (actuales >= maxUsuarios) {
			throw ApiException.badRequest("La empresa alcanzo el limite de usuarios permitido por su plan.");
		}
	}

	private Integer obtenerLimitePlan(Integer idEmpresa, String clave) {
		List<HistorialSuscripcion> activas = historialSuscripcionRepository.findActivasPorEmpresa(
				idEmpresa, LocalDate.now());
		if (activas.isEmpty() || activas.get(0).getPlan() == null) {
			return null;
		}
		List<PlanModulo> planModulos = planModuloRepository.findByPlan_IdPlan(activas.get(0).getPlan().getIdPlan());
		for (PlanModulo planModulo : planModulos) {
			Integer limite = obtenerLimiteDesdeConfiguracion(planModulo.getConfiguracion(), clave);
			if (limite != null) {
				return limite;
			}
			if (planModulo.getConfiguracion() != null) {
				String texto = planModulo.getConfiguracion().toLowerCase().replace("í", "i");
				if (texto.contains(clave) && texto.contains(LIMITE_ILIMITADO)) {
					return null;
				}
			}
		}
		return null;
	}

	private Integer obtenerLimiteDesdeConfiguracion(String configuracion, String clave) {
		if (configuracion == null || configuracion.isBlank()) {
			return null;
		}
		try {
			JsonNode node = objectMapper.readTree(configuracion);
			if (node.isObject() && node.has(clave)) {
				return normalizarLimite(node.get(clave), clave);
			}
			if (node.isTextual()) {
				return parseLimiteDesdeTexto(node.asText(), clave);
			}
		} catch (Exception ignored) {
			return parseLimiteDesdeTexto(configuracion, clave);
		}
		return parseLimiteDesdeTexto(configuracion, clave);
	}

	private Integer parseLimiteDesdeTexto(String configuracion, String clave) {
		Pattern pattern = Pattern.compile(
				Pattern.quote(clave) + "\\s*[:=]\\s*(.*?)(?=\\s+\\w+\\s*[:=]|[,;\\r\\n]|$)",
				Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(configuracion);
		if (!matcher.find()) {
			return null;
		}
		return normalizarLimiteTexto(matcher.group(1), clave);
	}

	private Integer normalizarLimite(JsonNode node, String clave) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (node.isInt() || node.isLong()) {
			return node.asInt();
		}
		return normalizarLimiteTexto(node.asText(), clave);
	}

	private Integer normalizarLimiteTexto(String valor, String clave) {
		if (valor == null) {
			return null;
		}
		String texto = valor.strip().toLowerCase().replace("í", "i");
		if (Set.of(LIMITE_ILIMITADO, "sin limites", "ilimitado", "ilimitados", "unlimited").contains(texto)) {
			return null;
		}
		try {
			return Integer.parseInt(texto);
		} catch (NumberFormatException ex) {
			throw ApiException.badRequest("La configuracion " + clave + " del plan no es valida.");
		}
	}

	private Sucursal requireSucursalDeEmpresa(Integer idEmpresa, Integer idSucursal) {
		return sucursalRepository.findByIdSucursalAndEmpresa_IdEmpresa(idSucursal, idEmpresa)
				.orElseThrow(() -> ApiException.notFound("Sucursal no encontrada para esta empresa."));
	}

	private Rol requireRolActivo(Integer idRol, Integer idEmpresa) {
		Rol rol = rolRepository.findById(idRol)
				.orElseThrow(() -> ApiException.notFound("Rol no encontrado o inactivo."));
		if (!Boolean.TRUE.equals(rol.getActivo())) {
			throw ApiException.notFound("Rol no encontrado o inactivo.");
		}
		if (rol.getIdEmpresa() != null && !rol.getIdEmpresa().equals(idEmpresa)) {
			throw ApiException.badRequest("El rol no pertenece a esta empresa.");
		}
		return rol;
	}

	private UsuarioRol crearUsuarioRol(Usuario usuario, Rol rol, Integer idEmpresa, Integer idSucursal, boolean activo) {
		UsuarioRol usuarioRol = new UsuarioRol();
		usuarioRol.setUsuario(usuario);
		usuarioRol.setRol(rol);
		usuarioRol.setIdEmpresa(idEmpresa);
		usuarioRol.setIdSucursal(idSucursal);
		usuarioRol.setFecha(LocalDate.now());
		usuarioRol.setActivo(activo);
		return usuarioRolRepository.saveAndFlush(usuarioRol);
	}

	private String generarCodigoCliente() {
		for (int i = 0; i < 20; i++) {
			StringBuilder codigo = new StringBuilder();
			codigo.append(RANDOM.nextInt(9) + 1);
			for (int j = 0; j < 8; j++) {
				codigo.append(RANDOM.nextInt(10));
			}
			String valor = codigo.toString();
			if (!clienteRepository.existsByCodigoCliente(valor)) {
				return valor;
			}
		}
		throw new IllegalStateException("No se pudo generar un codigo de cliente unico.");
	}

	private Integer intClaim(Claims claims, String key) {
		Object value = claims.get(key);
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value == null) {
			throw ApiException.badRequest("Invitacion invalida.");
		}
		return Integer.valueOf(String.valueOf(value));
	}

	private List<Integer> idSucursalesClaim(Claims claims) {
		Object raw = claims.get("id_sucursales");
		if (!(raw instanceof List<?> list)) {
			throw ApiException.badRequest("Invitacion invalida.");
		}
		List<Integer> ids = new ArrayList<>();
		for (Object item : list) {
			if (item instanceof Number number) {
				ids.add(number.intValue());
			} else {
				ids.add(Integer.valueOf(String.valueOf(item)));
			}
		}
		return ids;
	}

	private String nombreUsuario(Usuario usuario) {
		if (usuario.getPersona() != null && usuario.getPersona().getNombreCompleto() != null) {
			return usuario.getPersona().getNombreCompleto();
		}
		return usuario.getEmail();
	}

	private SucursalResponse toResponse(Sucursal sucursal) {
		Integer idEmpresa = sucursal.getEmpresa() != null ? sucursal.getEmpresa().getIdEmpresa() : null;
		return new SucursalResponse(
				sucursal.getIdSucursal(),
				idEmpresa,
				sucursal.getNombre(),
				sucursal.getDireccion(),
				sucursal.getTelefono(),
				sucursal.getCiudad(),
				sucursal.getFechaRegistro(),
				sucursal.getActivo());
	}

	private EmpresaEmpleadoResponse toEmpresaEmpleado(Empresa empresa) {
		if (empresa == null) {
			return null;
		}
		return new EmpresaEmpleadoResponse(
				empresa.getIdEmpresa(),
				empresa.getNombre(),
				empresa.getRazonSocial(),
				empresa.getNit(),
				empresa.getCorreo(),
				empresa.getFechaCreacion(),
				empresa.getActivo());
	}

	private PersonalEmpresaResponse toPersonalEmpresa(UsuarioRol usuarioRol) {
		return new PersonalEmpresaResponse(
				usuarioRol.getIdUsuarioRol(),
				usuarioRol.getUsuario() != null ? usuarioRol.getUsuario().getIdUsuario() : null,
				usuarioRol.getRol() != null ? usuarioRol.getRol().getIdRol() : null,
				usuarioRol.getIdEmpresa(),
				usuarioRol.getIdSucursal(),
				usuarioRol.getActivo(),
				toUsuarioEmpleado(usuarioRol.getUsuario()));
	}

	private UsuarioEmpleadoResponse toUsuarioEmpleado(Usuario usuario) {
		if (usuario == null) {
			return null;
		}
		Persona persona = usuario.getPersona();
		PersonaEmpleadoResponse personaDto = persona == null ? null : new PersonaEmpleadoResponse(
				persona.getIdPersona(),
				persona.getNombreCompleto(),
				persona.getFechaNacimiento(),
				persona.getGenero(),
				persona.getTelefono(),
				persona.getDocumento());
		return new UsuarioEmpleadoResponse(usuario.getIdUsuario(), usuario.getEmail(), usuario.getActivo(), personaDto);
	}

	private ClienteResponse toCliente(Cliente cliente) {
		return toCliente(cliente, null);
	}

	private ClienteResponse toCliente(Cliente cliente, BigDecimal saldoCalculado) {
		if (cliente == null) {
			return null;
		}
		Integer idCategoria = cliente.getCategoriaCliente() != null
				? cliente.getCategoriaCliente().getIdCategoriaCliente()
				: null;
		BigDecimal saldo = saldoCalculado != null ? saldoCalculado : (cliente.getSaldoCredito() != null ? cliente.getSaldoCredito() : BigDecimal.ZERO);
		return new ClienteResponse(
				cliente.getIdCliente(),
				cliente.getIdUsuario(),
				idCategoria,
				cliente.getCodigoCliente(),
				saldo,
				cliente.getLimiteCredito(),
				cliente.getActivo());
	}

	private String htmlInvitacionEmpleado(String nombre, String invitationLink) {
		return """
				<html>
				    <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;">
				        <div style="background-color: white; padding: 20px; border-radius: 8px; max-width: 600px; margin: 0 auto;">
				            <h2 style="color: #333;">Hola, %s</h2>
				            <p style="color: #666;">Recibiste una invitacion para unirte como empleado.</p>
				            <p style="color: #666;">Para aceptar la invitacion, ingresa al siguiente link:</p>
				            <p style="margin: 20px 0;">
				                <a href="%s" style="background-color: #007bff; color: white; padding: 12px 16px; text-decoration: none; border-radius: 5px;">
				                    Aceptar invitacion
				                </a>
				            </p>
				            <p style="color: #666; font-size: 12px;">Si el boton no funciona, copia y pega este link en tu navegador:</p>
				            <p style="color: #007bff; font-size: 12px;">%s</p>
				        </div>
				    </body>
				</html>
				""".formatted(nombre, invitationLink, invitationLink);
	}

	private String htmlInvitacionCliente(String nombre, String empresaNombre, String invitationLink) {
		return """
				<html>
				    <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;">
				        <div style="background-color: white; padding: 20px; border-radius: 8px; max-width: 600px; margin: 0 auto; box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);">
				            <h2 style="color: #333; margin-top: 0;">Hola, %s</h2>
				            <p style="color: #666; line-height: 1.6;">Recibiste una invitacion para convertirte en cliente de <strong>%s</strong>.</p>
				            <p style="color: #666; line-height: 1.6;">Para aceptar la invitacion y confirmar tu relacion con la empresa, haz clic en el siguiente boton:</p>
				            <p style="margin: 28px 0; text-align: center;">
				                <a href="%s" style="background-color: #007bff; color: white; padding: 12px 18px; text-decoration: none; border-radius: 6px; display: inline-block; font-weight: bold;">
				                    Aceptar invitacion
				                </a>
				            </p>
				            <p style="color: #666; font-size: 12px; margin-bottom: 6px;">Si el boton no funciona, copia y pega este link en tu navegador:</p>
				            <p style="color: #007bff; font-size: 12px; word-break: break-all;">%s</p>
				        </div>
				    </body>
				</html>
				""".formatted(nombre, empresaNombre, invitationLink, invitationLink);
	}
}
