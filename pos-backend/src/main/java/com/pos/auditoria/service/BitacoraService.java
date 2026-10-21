package com.pos.auditoria.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.pos.auditoria.domain.Bitacora;
import com.pos.auditoria.dto.BitacoraDtos;
import com.pos.auditoria.repo.BitacoraRepository;
import com.pos.usuarios.domain.Usuario;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class BitacoraService {

	private static final String HASH_INICIAL = "0".repeat(64);
	private static final int MAX_PAGE_SIZE = 200;

	private final BitacoraRepository bitacoraRepository;
	private final TransactionTemplate nuevaTransaccion;
	private final Object candado = new Object();

	public BitacoraService(BitacoraRepository bitacoraRepository, PlatformTransactionManager transactionManager) {
		this.bitacoraRepository = bitacoraRepository;
		this.nuevaTransaccion = new TransactionTemplate(transactionManager);
		this.nuevaTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** Datos de un evento a registrar. Los campos de la peticion HTTP se completan solos si hay una en curso. */
	public record Evento(
			Usuario usuario,
			String email,
			Integer idEmpresa,
			String accion,
			String modulo,
			String metodo,
			String ruta,
			Integer estadoHttp,
			String detalle) {
	}

	/**
	 * Registra un evento en su propia transaccion, para que quede guardado aunque la operacion
	 * que lo origino falle. Nunca lanza excepciones hacia el llamador.
	 */
	public void registrar(Evento evento) {
		try {
			HttpServletRequest request = requestActual();
			synchronized (candado) {
				nuevaTransaccion.executeWithoutResult(status -> guardar(evento, request));
			}
		} catch (Exception ex) {
			System.out.println("[BITACORA] No se pudo registrar el evento: " + ex.getMessage());
		}
	}

	private void guardar(Evento evento, HttpServletRequest request) {
		Bitacora b = new Bitacora();
		b.setFecha(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
		if (evento.usuario() != null) {
			b.setIdUsuario(evento.usuario().getIdUsuario());
			b.setEmailUsuario(evento.usuario().getEmail());
		} else {
			b.setEmailUsuario(recortar(evento.email(), 255));
		}
		b.setIdEmpresa(evento.idEmpresa());
		b.setAccion(recortar(evento.accion(), 40));
		b.setModulo(recortar(evento.modulo(), 60));
		b.setMetodo(recortar(evento.metodo() != null ? evento.metodo() : request != null ? request.getMethod() : null, 10));
		b.setRuta(recortar(evento.ruta() != null ? evento.ruta() : request != null ? request.getRequestURI() : null, 255));
		b.setEstadoHttp(evento.estadoHttp());
		b.setDetalle(recortar(evento.detalle(), 500));
		if (request != null) {
			b.setIp(recortar(ipCliente(request), 64));
			b.setUserAgent(recortar(request.getHeader("User-Agent"), 255));
		}
		String anterior = bitacoraRepository.findTopByOrderByIdBitacoraDesc()
				.map(Bitacora::getHash)
				.orElse(HASH_INICIAL);
		b.setHashAnterior(anterior);
		b.setHash(calcularHash(anterior, b));
		bitacoraRepository.save(b);
	}

	@Transactional(readOnly = true)
	public BitacoraDtos.Pagina buscar(BitacoraDtos.Filtro filtro, Integer idEmpresaFijo) {
		Specification<Bitacora> spec = (root, query, cb) -> cb.conjunction();
		Integer idEmpresa = idEmpresaFijo != null ? idEmpresaFijo : filtro.idEmpresa();
		if (idEmpresa != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("idEmpresa"), idEmpresa));
		}
		if (filtro.desde() != null) {
			LocalDateTime desde = filtro.desde().atStartOfDay();
			spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fecha"), desde));
		}
		if (filtro.hasta() != null) {
			LocalDateTime hasta = filtro.hasta().plusDays(1).atStartOfDay();
			spec = spec.and((root, query, cb) -> cb.lessThan(root.get("fecha"), hasta));
		}
		if (noVacio(filtro.accion())) {
			String accion = filtro.accion().trim().toUpperCase();
			spec = spec.and((root, query, cb) -> cb.equal(root.get("accion"), accion));
		}
		if (noVacio(filtro.modulo())) {
			String modulo = filtro.modulo().trim().toUpperCase();
			spec = spec.and((root, query, cb) -> cb.equal(root.get("modulo"), modulo));
		}
		if (noVacio(filtro.usuario())) {
			String usuario = "%" + filtro.usuario().trim().toLowerCase() + "%";
			spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("emailUsuario")), usuario));
		}
		int page = filtro.page() == null || filtro.page() < 0 ? 0 : filtro.page();
		int size = filtro.size() == null || filtro.size() <= 0 ? 25 : Math.min(filtro.size(), MAX_PAGE_SIZE);
		Page<Bitacora> resultado = bitacoraRepository.findAll(
				spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "idBitacora")));
		return new BitacoraDtos.Pagina(
				resultado.getContent().stream().map(BitacoraDtos.Registro::from).toList(),
				resultado.getTotalElements(),
				page,
				size);
	}

	/** Recorre toda la cadena y verifica que ningun registro haya sido alterado o eliminado. */
	@Transactional(readOnly = true)
	public BitacoraDtos.Verificacion verificar() {
		List<Bitacora> registros = bitacoraRepository.findAll(Sort.by(Sort.Direction.ASC, "idBitacora"));
		String anterior = HASH_INICIAL;
		List<Long> alterados = new ArrayList<>();
		for (Bitacora b : registros) {
			boolean enlaceValido = anterior.equals(b.getHashAnterior());
			boolean contenidoValido = calcularHash(b.getHashAnterior(), b).equals(b.getHash());
			if (!enlaceValido || !contenidoValido) {
				alterados.add(b.getIdBitacora());
			}
			anterior = b.getHash();
		}
		return new BitacoraDtos.Verificacion(
				alterados.isEmpty(),
				registros.size(),
				alterados.size() > 50 ? alterados.subList(0, 50) : alterados,
				alterados.isEmpty()
						? "La bitacora esta integra: ningun registro fue modificado ni eliminado."
						: "Se detectaron " + alterados.size() + " registro(s) alterados o faltantes.");
	}

	@Transactional(readOnly = true)
	public Map<String, Long> resumenHoy() {
		Map<String, Long> resumen = new LinkedHashMap<>();
		resumen.put("total", bitacoraRepository.count());
		resumen.put("hoy", bitacoraRepository.countByFechaAfter(LocalDate.now().atStartOfDay()));
		return resumen;
	}

	private String calcularHash(String anterior, Bitacora b) {
		String contenido = String.join("|",
				anterior,
				String.valueOf(b.getFecha()),
				String.valueOf(b.getIdUsuario()),
				String.valueOf(b.getEmailUsuario()),
				String.valueOf(b.getIdEmpresa()),
				String.valueOf(b.getAccion()),
				String.valueOf(b.getModulo()),
				String.valueOf(b.getMetodo()),
				String.valueOf(b.getRuta()),
				String.valueOf(b.getEstadoHttp()),
				String.valueOf(b.getIp()),
				String.valueOf(b.getDetalle()));
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(contenido.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception ex) {
			throw new IllegalStateException("SHA-256 no disponible", ex);
		}
	}

	private HttpServletRequest requestActual() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
			return attrs.getRequest();
		}
		return null;
	}

	private String ipCliente(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	private static boolean noVacio(String value) {
		return value != null && !value.isBlank();
	}

	private static String recortar(String value, int max) {
		if (value == null) {
			return null;
		}
		return value.length() <= max ? value : value.substring(0, max);
	}
}
