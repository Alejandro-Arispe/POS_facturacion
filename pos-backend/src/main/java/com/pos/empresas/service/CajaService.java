package com.pos.empresas.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;
import com.pos.common.security.SecurityUtils;
import com.pos.empresas.domain.Caja;
import com.pos.empresas.domain.CajaCierreDetalle;
import com.pos.empresas.domain.CajaSesion;
import com.pos.empresas.domain.MovimientoCaja;
import com.pos.empresas.domain.Sucursal;
import com.pos.empresas.domain.TipoMovimientoCaja;
import com.pos.empresas.dto.CajaCierreDetalleCreate;
import com.pos.empresas.dto.CajaCierreDetalleResponse;
import com.pos.empresas.dto.CajaCreate;
import com.pos.empresas.dto.CajaResponse;
import com.pos.empresas.dto.CajaSesionActivaResponse;
import com.pos.empresas.dto.CajaSesionCierreResponse;
import com.pos.empresas.dto.CajaSesionCreate;
import com.pos.empresas.dto.CajaSesionResponse;
import com.pos.empresas.dto.CajaUpdate;
import com.pos.empresas.dto.MovimientoCajaCreate;
import com.pos.empresas.dto.MovimientoCajaPorMetodoPagoResponse;
import com.pos.empresas.dto.MovimientoCajaResponse;
import com.pos.empresas.dto.ResumenMovimientosCajaResponse;
import com.pos.empresas.repo.CajaCierreDetalleRepository;
import com.pos.empresas.repo.CajaRepository;
import com.pos.empresas.repo.CajaSesionRepository;
import com.pos.empresas.repo.MovimientoCajaRepository;
import com.pos.empresas.repo.SucursalRepository;
import com.pos.empresas.repo.TipoMovimientoCajaRepository;
import com.pos.usuarios.domain.Usuario;
import com.pos.usuarios.repo.UsuarioRepository;
import com.pos.ventas.domain.MetodoPago;
import com.pos.ventas.repo.MetodoPagoRepository;
import java.util.Optional;

@Service
public class CajaService {

	private final EmpresaAccess empresaAccess;
	private final CajaRepository cajaRepository;
	private final CajaSesionRepository cajaSesionRepository;
	private final CajaCierreDetalleRepository cajaCierreDetalleRepository;
	private final MovimientoCajaRepository movimientoCajaRepository;
	private final SucursalRepository sucursalRepository;
	private final TipoMovimientoCajaRepository tipoMovimientoCajaRepository;
	private final MetodoPagoRepository metodoPagoRepository;
	private final UsuarioRepository usuarioRepository;

	public CajaService(
			EmpresaAccess empresaAccess,
			CajaRepository cajaRepository,
			CajaSesionRepository cajaSesionRepository,
			CajaCierreDetalleRepository cajaCierreDetalleRepository,
			MovimientoCajaRepository movimientoCajaRepository,
			SucursalRepository sucursalRepository,
			TipoMovimientoCajaRepository tipoMovimientoCajaRepository,
			MetodoPagoRepository metodoPagoRepository,
			UsuarioRepository usuarioRepository) {
		this.empresaAccess = empresaAccess;
		this.cajaRepository = cajaRepository;
		this.cajaSesionRepository = cajaSesionRepository;
		this.cajaCierreDetalleRepository = cajaCierreDetalleRepository;
		this.movimientoCajaRepository = movimientoCajaRepository;
		this.sucursalRepository = sucursalRepository;
		this.tipoMovimientoCajaRepository = tipoMovimientoCajaRepository;
		this.metodoPagoRepository = metodoPagoRepository;
		this.usuarioRepository = usuarioRepository;
	}

	@Transactional
	public CajaResponse crear(Integer idSucursal, CajaCreate datos) {
		Usuario usuario = SecurityUtils.currentUser();
		Sucursal sucursal = sucursalRepository.findById(idSucursal)
				.orElseThrow(() -> ApiException.notFound("Sucursal no encontrada."));
		empresaAccess.requirePermiso(usuario,sucursal.getEmpresa().getIdEmpresa(),idSucursal,"cash_register:create");
		empresaAccess.requireCashCapacity(sucursal.getEmpresa().getIdEmpresa(), idSucursal);

		try {
			Caja caja = new Caja();
			caja.setSucursal(sucursal);
			caja.setNombre(datos.nombre());
			caja.setCodigo(datos.codigo());
			caja.setFechaCreacion(LocalDate.now());
			caja.setActivo(Boolean.TRUE);
			return toCajaResponse(cajaRepository.saveAndFlush(caja));
		} catch (DataIntegrityViolationException ex) {
			throw ApiException.badRequest("No se pudo crear la caja.");
		}
	}

	@Transactional(readOnly = true)
	public List<CajaResponse> listar(Integer idEmpresa, Integer idSucursal) {
		requireSucursal(idEmpresa, idSucursal);
        empresaAccess.requirePermiso(SecurityUtils.currentUser(),idEmpresa,idSucursal,"cash_register:read");
		Usuario usuario = SecurityUtils.currentUser();
		return cajaRepository.findBySucursal_IdSucursal(idSucursal).stream()
				.map(caja -> toCajaResponse(caja, usuario))
				.toList();
	}

	@Transactional(readOnly = true)
	public CajaResponse obtener(Integer idEmpresa, Integer idSucursal, Integer idCaja) {
		Usuario usuario = SecurityUtils.currentUser();
        empresaAccess.requirePermiso(usuario,idEmpresa,idSucursal,"cash_register:read");
		return toCajaResponse(requireCaja(idEmpresa, idSucursal, idCaja), usuario);
	}

	@Transactional
	public CajaResponse actualizar(Integer idEmpresa, Integer idSucursal, Integer idCaja, CajaUpdate datos) {
        empresaAccess.requirePermiso(SecurityUtils.currentUser(),idEmpresa,idSucursal,"cash_register:update");
		Caja caja = requireCaja(idEmpresa, idSucursal, idCaja);
		if (!Boolean.TRUE.equals(caja.getActivo()) && Boolean.TRUE.equals(datos.activo())) {
			empresaAccess.requireCashCapacity(idEmpresa, idSucursal);
		}
		caja.setNombre(datos.nombre());
		caja.setCodigo(datos.codigo());
		caja.setActivo(datos.activo());
		Usuario usuario = SecurityUtils.currentUser();
		return toCajaResponse(cajaRepository.save(caja), usuario);
	}

	@Transactional
	public CajaSesionResponse crearSesion(Integer idCaja, CajaSesionCreate datos) {
		Usuario usuario = SecurityUtils.currentUser();
        usuarioRepository.lockById(usuario.getIdUsuario()).orElseThrow(() -> ApiException.notFound("Usuario no encontrado."));
        cajaRepository.lockById(idCaja).orElseThrow(() -> ApiException.notFound("Caja no encontrada."));
		Caja caja = cajaRepository.findWithSucursalAndEmpresa(idCaja)
				.orElseThrow(() -> ApiException.notFound("Caja no encontrada."));
        empresaAccess.requirePermiso(usuario,caja.getSucursal().getEmpresa().getIdEmpresa(),caja.getSucursal().getIdSucursal(),"cash_register:open");
        if (!Boolean.TRUE.equals(caja.getActivo())) throw ApiException.badRequest("La caja está inactiva.");
        com.pos.ventas.service.SalePricing.money(datos.montoInicial(),"monto inicial");

		cajaSesionRepository.findAbiertasPorUsuario(usuario.getIdUsuario(), "Abierto").stream()
				.findFirst()
				.ifPresent(sesion -> conflictSesionAbierta(
						sesion.getCaja().getIdCaja(),
						sesion.getIdCajaSesion(),
						"Tienes una sesion abierta aun"));

		cajaSesionRepository.findFirstByCaja_IdCajaAndEstado(idCaja, "Abierto")
				.ifPresent(sesion -> conflictSesionAbierta(
						idCaja,
						sesion.getIdCajaSesion(),
						"Esta caja tiene ya una sesion abierta"));

		MetodoPago efectivo = metodoPagoRepository.findByNombreIgnoreCase("EFECTIVO")
				.orElseThrow(() -> ApiException.notFound("Metodo de pago EFECTIVO no encontrado."));

		try {
			CajaSesion sesion = new CajaSesion();
			sesion.setCaja(caja);
			sesion.setIdUsuario(usuario.getIdUsuario());
			sesion.setFechaApertura(LocalDateTime.now());
			sesion.setFechaCierre(null);
			sesion.setMontoInicial(datos.montoInicial());
			sesion.setMontoFinal(null);
			sesion.setEstado("Abierto");
			sesion.setNota(datos.nota());
			sesion = cajaSesionRepository.saveAndFlush(sesion);

            TipoMovimientoCaja apertura = tipoMovimientoCajaRepository.findByNombreIgnoreCase("APERTURA")
                .orElseThrow(() -> ApiException.badRequest("Tipo de movimiento APERTURA no configurado."));
            MovimientoCaja movimiento = new MovimientoCaja();
            movimiento.setIdMetodoPago(efectivo.getIdMetodoPago()); movimiento.setTipoMovimientoCaja(apertura);
            movimiento.setCajaSesion(sesion); movimiento.setIdUsuario(usuario.getIdUsuario());
            movimiento.setFecha(LocalDateTime.now()); movimiento.setMonto(datos.montoInicial());
            movimiento.setConcepto("APERTURA " + sesion.getIdCajaSesion());
            movimientoCajaRepository.saveAndFlush(movimiento);

			return toSesionResponse(sesion);
		} catch (DataIntegrityViolationException ex) {
			throw ApiException.badRequest("No se pudo crear la sesion de caja.");
		}
	}

	@Transactional
	public MovimientoCajaResponse crearMovimiento(Integer idCajaSesion, MovimientoCajaCreate datos) {
		Usuario usuario = SecurityUtils.currentUser();
        CajaSesion sesion = requireWritableSession(usuario,idCajaSesion,"cash_movement:create");
        if (!"Abierto".equalsIgnoreCase(sesion.getEstado())) throw ApiException.badRequest("La sesión debe estar abierta.");
        com.pos.ventas.service.SalePricing.money(datos.monto(),"movimiento");
        if (datos.monto().signum()==0 || datos.idMetodoPago()==null) throw ApiException.badRequest("Indica un monto positivo y un método de pago.");

		TipoMovimientoCaja tipo = tipoMovimientoCajaRepository.findById(datos.idTipoMovimientoCaja())
				.orElseThrow(() -> ApiException.notFound("Tipo de movimiento de caja no encontrado."));
        if (!Set.of("INGRESO","EGRESO","AJUSTE_POSITIVO","AJUSTE_NEGATIVO").contains(tipo.getNombre().toUpperCase(java.util.Locale.ROOT)))
            throw ApiException.badRequest("Apertura y cierre se generan desde sus operaciones específicas.");
		if (datos.idMetodoPago() != null) {
			metodoPagoRepository.findById(datos.idMetodoPago())
					.orElseThrow(() -> ApiException.notFound("Metodo de pago no encontrado."));
		}

		try {
			MovimientoCaja movimiento = new MovimientoCaja();
			movimiento.setIdMetodoPago(datos.idMetodoPago());
			movimiento.setTipoMovimientoCaja(tipo);
			movimiento.setCajaSesion(sesion);
			movimiento.setIdUsuario(usuario.getIdUsuario());
			movimiento.setFecha(LocalDateTime.now());
			movimiento.setMonto(datos.monto());
			movimiento.setConcepto(datos.concepto());
			return toMovimientoResponse(movimientoCajaRepository.saveAndFlush(movimiento));
		} catch (DataIntegrityViolationException ex) {
			throw ApiException.badRequest("No se pudo registrar el movimiento de caja.");
		}
	}

	@Transactional(readOnly = true)
	public List<MovimientoCajaResponse> listarMovimientos(Integer idCajaSesion) {
		Usuario usuario = SecurityUtils.currentUser();
        sessionPermission(usuario,requireSesionDeUsuario(usuario,idCajaSesion),"cash_movement:read");
		return movimientoCajaRepository.findBySesionFetchTipo(idCajaSesion).stream()
				.map(this::toMovimientoResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public ResumenMovimientosCajaResponse resumenMovimientos(Integer idCajaSesion) {
		Usuario usuario = SecurityUtils.currentUser();
        sessionPermission(usuario,requireSesionDeUsuario(usuario,idCajaSesion),"cash_movement:read");
        return calculateSummary(idCajaSesion);
    }
    private ResumenMovimientosCajaResponse calculateSummary(Integer idCajaSesion) {

		List<MovimientoCaja> movimientos = movimientoCajaRepository.findBySesionFetchTipo(idCajaSesion);
		Set<Integer> idsMetodo = movimientos.stream()
				.map(MovimientoCaja::getIdMetodoPago)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Map<Integer, String> nombresMetodo = metodoPagoRepository.findAllById(idsMetodo).stream()
				.collect(Collectors.toMap(MetodoPago::getIdMetodoPago, MetodoPago::getNombre));

		Map<Integer, GrupoMetodo> grupos = new LinkedHashMap<>();
		BigDecimal montoEsperadoTotal = BigDecimal.ZERO;

		for (MovimientoCaja movimiento : movimientos) {
			BigDecimal signo = signoTipo(movimiento.getTipoMovimientoCaja() != null
					? movimiento.getTipoMovimientoCaja().getNombre()
					: null);
			BigDecimal montoBase = movimiento.getMonto() != null ? movimiento.getMonto() : BigDecimal.ZERO;
			BigDecimal montoAjustado = montoBase.multiply(signo);
			montoEsperadoTotal = montoEsperadoTotal.add(montoAjustado);

			GrupoMetodo grupo = grupos.computeIfAbsent(movimiento.getIdMetodoPago(), id -> {
				GrupoMetodo creado = new GrupoMetodo();
				creado.idMetodoPago = id;
				creado.metodoPago = id != null ? nombresMetodo.get(id) : null;
				return creado;
			});
			grupo.movimientos.add(movimiento);
			grupo.montoEsperado = grupo.montoEsperado.add(montoAjustado);
			if (montoAjustado.compareTo(BigDecimal.ZERO) >= 0) {
				grupo.totalIngresos = grupo.totalIngresos.add(montoBase);
			} else {
				grupo.totalEgresos = grupo.totalEgresos.add(montoBase);
			}
		}

		List<MovimientoCajaPorMetodoPagoResponse> resumen = grupos.values().stream()
				.map(grupo -> new MovimientoCajaPorMetodoPagoResponse(
						grupo.idMetodoPago,
						grupo.metodoPago,
						grupo.totalIngresos,
						grupo.totalEgresos,
						grupo.montoEsperado,
						grupo.movimientos.stream().map(this::toMovimientoResponse).toList()))
				.toList();

		return new ResumenMovimientosCajaResponse(idCajaSesion, montoEsperadoTotal, resumen);
	}

	@Transactional
	public CajaSesionCierreResponse cerrarSesion(Integer idCajaSesion, List<CajaCierreDetalleCreate> cierres) {
		Usuario usuario = SecurityUtils.currentUser();
		if (cierres == null || cierres.isEmpty()) {
			throw ApiException.badRequest("Debe enviar al menos un cierre de caja.");
		}

        CajaSesion sesion = requireWritableSession(usuario,idCajaSesion,"cash_register:close");
        Map<Integer,BigDecimal> expected=new LinkedHashMap<>();
        for(var summary:calculateSummary(idCajaSesion).resumenPorMetodoPago()) expected.put(summary.idMetodoPago(),summary.montoEsperado());
        Set<Integer> seen=new java.util.HashSet<>();
        for(var row:cierres) {
            if(row==null || row.idMetodoPago()==null || !seen.add(row.idMetodoPago())) throw ApiException.badRequest("No repitas métodos de pago en el cierre.");
            var real=com.pos.ventas.service.SalePricing.money(row.montoReal(),"monto real");
            BigDecimal amount=expected.getOrDefault(row.idMetodoPago(),BigDecimal.ZERO);
            if(row.montoEsperado()!=null && row.montoEsperado().compareTo(amount)!=0) throw ApiException.badRequest("El monto esperado cambió. Actualiza el resumen de caja.");
            if(row.diferencia()!=null && row.diferencia().compareTo(real.subtract(amount))!=0) throw ApiException.badRequest("La diferencia debe coincidir con el cálculo del servidor.");
        }
        if(!seen.containsAll(expected.keySet())) throw ApiException.badRequest("Incluye todos los métodos de pago del resumen para cerrar.");
		if ("Cerrado".equals(sesion.getEstado())) {
			throw ApiException.badRequest("La sesion de caja ya esta cerrada.");
		}

		TipoMovimientoCaja tipoCierre = tipoMovimientoCajaRepository.findByNombreIgnoreCase("CIERRE")
				.orElseThrow(() -> ApiException.notFound("Tipo de movimiento CIERRE no encontrado."));

		try {
			List<CajaCierreDetalle> cierresCreados = new ArrayList<>();
			List<MovimientoCaja> movimientosCierre = new ArrayList<>();
			BigDecimal totalMontoReal = BigDecimal.ZERO;
			BigDecimal totalMontoEsperado = BigDecimal.ZERO;

			for (CajaCierreDetalleCreate cierre : cierres) {
				MetodoPago metodoPago = metodoPagoRepository.findById(cierre.idMetodoPago())
						.orElseThrow(() -> ApiException.notFound("Metodo de pago no encontrado."));

				CajaCierreDetalle detalle = new CajaCierreDetalle();
				detalle.setCajaSesion(sesion);
				detalle.setIdMetodoPago(cierre.idMetodoPago());
				detalle.setMontoEsperado(expected.getOrDefault(cierre.idMetodoPago(),BigDecimal.ZERO));
				detalle.setMontoReal(cierre.montoReal());
				detalle.setDiferencia(cierre.montoReal().subtract(detalle.getMontoEsperado()));
				detalle.setObservacion(cierre.observacion());
				detalle = cajaCierreDetalleRepository.saveAndFlush(detalle);
				cierresCreados.add(detalle);

				totalMontoReal = totalMontoReal.add(detalle.getMontoReal() != null ? detalle.getMontoReal() : BigDecimal.ZERO);
				totalMontoEsperado = totalMontoEsperado.add(
						detalle.getMontoEsperado() != null ? detalle.getMontoEsperado() : BigDecimal.ZERO);

				MovimientoCaja movimiento = new MovimientoCaja();
				movimiento.setIdMetodoPago(detalle.getIdMetodoPago());
				movimiento.setTipoMovimientoCaja(tipoCierre);
				movimiento.setCajaSesion(sesion);
				movimiento.setIdUsuario(usuario.getIdUsuario());
				movimiento.setFecha(LocalDateTime.now());
				movimiento.setMonto(detalle.getMontoEsperado());
				movimiento.setConcepto("CIERRE " + sesion.getIdCajaSesion() + " - " + metodoPago.getNombre());
				movimientosCierre.add(movimientoCajaRepository.saveAndFlush(movimiento));
			}

			sesion.setMontoFinal(totalMontoEsperado);
			sesion.setEstado("Cerrado");
			sesion.setFechaCierre(LocalDateTime.now());
			cajaSesionRepository.save(sesion);

			return new CajaSesionCierreResponse(
					sesion.getIdCajaSesion(),
					sesion.getMontoInicial(),
					totalMontoReal,
					sesion.getMontoFinal(),
					sesion.getEstado(),
					sesion.getFechaCierre(),
					movimientosCierre.stream().map(this::toMovimientoResponse).toList(),
					cierresCreados.stream().map(this::toCierreResponse).toList());
		} catch (DataIntegrityViolationException ex) {
			throw ApiException.badRequest("No se pudo cerrar la sesion de caja.");
		}
	}

	private Sucursal requireSucursal(Integer idEmpresa, Integer idSucursal) {
		empresaAccess.requireSucursal(SecurityUtils.currentUser(), idEmpresa,idSucursal);
		return sucursalRepository.findByIdSucursalAndEmpresa_IdEmpresa(idSucursal, idEmpresa)
				.orElseThrow(() -> ApiException.notFound("Sucursal no encontrada para esta empresa."));
	}

	private Caja requireCaja(Integer idEmpresa, Integer idSucursal, Integer idCaja) {
		requireSucursal(idEmpresa, idSucursal);
		return cajaRepository.findByIdCajaAndSucursal_IdSucursal(idCaja, idSucursal)
				.orElseThrow(() -> ApiException.notFound("Caja no encontrada para esta sucursal."));
	}

	private CajaSesion requireSesionDeUsuario(Usuario usuario, Integer idCajaSesion) {
		CajaSesion sesion = cajaSesionRepository.findWithCajaSucursalEmpresa(idCajaSesion)
				.orElseThrow(() -> ApiException.notFound("Sesion de caja no encontrada."));
		empresaAccess.requireSucursal(usuario, sesion.getCaja().getSucursal().getEmpresa().getIdEmpresa(),sesion.getCaja().getSucursal().getIdSucursal());
		return sesion;
	}

    private void sessionPermission(Usuario user,CajaSesion session,String code) {
        var branch=session.getCaja().getSucursal();
        empresaAccess.requirePermiso(user,branch.getEmpresa().getIdEmpresa(),branch.getIdSucursal(),code);
    }
    private CajaSesion requireWritableSession(Usuario user,Integer id,String code) {
        cajaSesionRepository.lockById(id).orElseThrow(() -> ApiException.notFound("Sesión no encontrada."));
        CajaSesion session=requireSesionDeUsuario(user,id);
        sessionPermission(user,session,code);
        if(!user.getIdUsuario().equals(session.getIdUsuario())) throw ApiException.forbidden("Solo el operador de la sesión puede modificarla.");
        return session;
    }

	private void conflictSesionAbierta(Integer idCaja, Integer idCajaSesion, String mensaje) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("id_caja", idCaja);
		detail.put("id_caja_sesion", idCajaSesion);
		detail.put("detail", mensaje);
		throw ApiException.conflict(detail, mensaje);
	}

	private BigDecimal signoTipo(String nombreTipo) {
		String nombre = nombreTipo == null ? "" : nombreTipo.strip().toUpperCase();
		if ("EGRESO".equals(nombre) || "AJUSTE_NEGATIVO".equals(nombre)) {
			return new BigDecimal("-1");
		}
		if ("CIERRE".equals(nombre)) {
			return BigDecimal.ZERO;
		}
		return BigDecimal.ONE;
	}

	private CajaResponse toCajaResponse(Caja caja) {
		Usuario usuario = null;
		try {
			usuario = SecurityUtils.currentUser();
		} catch (Exception ignored) {
		}
		return toCajaResponse(caja, usuario);
	}

	private CajaResponse toCajaResponse(Caja caja, Usuario usuario) {
		Integer idSucursal = caja.getSucursal() != null ? caja.getSucursal().getIdSucursal() : null;
		if (caja.getActivo() == null || !caja.getActivo()) {
			return new CajaResponse(
					caja.getIdCaja(),
					idSucursal,
					caja.getNombre(),
					caja.getCodigo(),
					caja.getFechaCreacion(),
					caja.getActivo(),
					"inactiva",
					null);
		}

		Optional<CajaSesion> sesionOpt = cajaSesionRepository.findFirstByCaja_IdCajaAndEstado(caja.getIdCaja(), "Abierto");
		if (sesionOpt.isEmpty()) {
			return new CajaResponse(
					caja.getIdCaja(),
					idSucursal,
					caja.getNombre(),
					caja.getCodigo(),
					caja.getFechaCreacion(),
					caja.getActivo(),
					"disponible",
					null);
		}

		CajaSesion sesion = sesionOpt.get();
		boolean esUsuarioActual = usuario != null && usuario.getIdUsuario().equals(sesion.getIdUsuario());
		String estadoOperativo = esUsuarioActual ? "abierta_por_mi" : "en_uso";
		String nombreUsuario = null;
		if (usuarioRepository != null && sesion.getIdUsuario() != null) {
			nombreUsuario = usuarioRepository.findById(sesion.getIdUsuario())
					.map(u -> u.getPersona() != null ? u.getPersona().getNombreCompleto() : u.getEmail())
					.orElse(null);
		}

		CajaSesionActivaResponse sesionActiva = new CajaSesionActivaResponse(
				sesion.getIdCajaSesion(),
				sesion.getIdUsuario(),
				nombreUsuario,
				sesion.getFechaApertura() != null ? sesion.getFechaApertura().toString() : null,
				esUsuarioActual);

		return new CajaResponse(
				caja.getIdCaja(),
				idSucursal,
				caja.getNombre(),
				caja.getCodigo(),
				caja.getFechaCreacion(),
				caja.getActivo(),
				estadoOperativo,
				sesionActiva);
	}

	private CajaSesionResponse toSesionResponse(CajaSesion sesion) {
		Integer idCaja = sesion.getCaja() != null ? sesion.getCaja().getIdCaja() : null;
		return new CajaSesionResponse(
				sesion.getIdCajaSesion(),
				idCaja,
				sesion.getIdUsuario(),
				sesion.getFechaApertura(),
				sesion.getFechaCierre(),
				sesion.getMontoInicial(),
				sesion.getMontoFinal(),
				sesion.getEstado(),
				sesion.getNota());
	}

	private MovimientoCajaResponse toMovimientoResponse(MovimientoCaja movimiento) {
		Integer idTipo = movimiento.getTipoMovimientoCaja() != null
				? movimiento.getTipoMovimientoCaja().getIdTipoMovimientoCaja()
				: null;
		Integer idSesion = movimiento.getCajaSesion() != null
				? movimiento.getCajaSesion().getIdCajaSesion()
				: null;
		return new MovimientoCajaResponse(
				movimiento.getIdMovimientoCaja(),
				movimiento.getIdMetodoPago(),
				idTipo,
				idSesion,
				movimiento.getIdUsuario(),
				movimiento.getFecha(),
				movimiento.getMonto(),
				movimiento.getConcepto());
	}

	private CajaCierreDetalleResponse toCierreResponse(CajaCierreDetalle detalle) {
		Integer idSesion = detalle.getCajaSesion() != null ? detalle.getCajaSesion().getIdCajaSesion() : null;
		return new CajaCierreDetalleResponse(
				detalle.getIdCajaCierreDetalle(),
				detalle.getIdMetodoPago(),
				idSesion,
				detalle.getMontoEsperado(),
				detalle.getMontoReal(),
				detalle.getDiferencia(),
				detalle.getObservacion());
	}

	private static final class GrupoMetodo {
		private Integer idMetodoPago;
		private String metodoPago;
		private BigDecimal totalIngresos = BigDecimal.ZERO;
		private BigDecimal totalEgresos = BigDecimal.ZERO;
		private BigDecimal montoEsperado = BigDecimal.ZERO;
		private final List<MovimientoCaja> movimientos = new ArrayList<>();
	}
}
