package com.pos.auditoria.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

import com.pos.auditoria.CajaNegraWriter;
import com.pos.auditoria.domain.Bitacora;
import com.pos.auditoria.dto.BitacoraDtos;
import com.pos.auditoria.repo.BitacoraRepository;
import com.pos.usuarios.domain.Usuario;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class BitacoraService {

	private static final String HASH_INICIAL = "0".repeat(64);
	private static final int MAX_PAGE_SIZE = 200;
	/** Consonantes cuyo plural en español agrega "es" (sucursal, rol) y no solo "s" (cliente, venta). */
	private static final String CONSONANTES_CON_ES = "lrndzjysx";

	private final BitacoraRepository bitacoraRepository;
	private final CajaNegraWriter cajaNegra;
	private final TransactionTemplate nuevaTransaccion;
	private final Object candado = new Object();

	public BitacoraService(
			BitacoraRepository bitacoraRepository,
			CajaNegraWriter cajaNegra,
			PlatformTransactionManager transactionManager) {
		this.bitacoraRepository = bitacoraRepository;
		this.cajaNegra = cajaNegra;
		this.nuevaTransaccion = new TransactionTemplate(transactionManager);
		this.nuevaTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** Recurso e identificador leidos de una ruta. El id de empresa en /empresas/{id}/... no cuenta. */
	public record RecursoRuta(String recurso, String idRecurso) {
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
				Bitacora guardada = nuevaTransaccion.execute(status -> guardar(evento, request));
				if (guardada != null) {
					cajaNegra.anexar(guardada);
				}
			}
		} catch (Exception ex) {
			System.out.println("[BITACORA] No se pudo registrar el evento: " + ex.getMessage());
		}
	}

	private Bitacora guardar(Evento evento, HttpServletRequest request) {
		Bitacora b = new Bitacora();
		LocalDateTime ahora = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
		b.setFecha(ahora);
		b.setHoraServidor(ahora);
		b.setHoraCliente(request != null ? parseHoraCliente(request.getHeader("X-Client-Time")) : null);
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
		String ruta = evento.ruta() != null ? evento.ruta() : request != null ? request.getRequestURI() : null;
		b.setRuta(recortar(ruta, 255));
		RecursoRuta recurso = recursoDesdeRuta(ruta);
		b.setRecurso(recortar(recurso.recurso(), 80));
		b.setIdRecurso(recortar(recurso.idRecurso(), 40));
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
		return bitacoraRepository.save(b);
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
			boolean contenidoValido = contenidoIntegro(b);
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

	/**
	 * Hash de filas nuevas. hora_cliente, recurso e id_recurso van al final para no cambiar
	 * el calculo de las filas que se guardaron antes de existir esas columnas.
	 */
	private String calcularHash(String anterior, Bitacora b) {
		return sha256(contenidoBase(anterior, b) + sufijoNuevo(b));
	}

	/**
	 * Una fila vieja tiene las columnas nuevas en nulo y su hash no incluye el sufijo.
	 * Una fila nueva siempre se hashea con el sufijo, aunque esos valores sean nulos.
	 */
	private boolean contenidoIntegro(Bitacora b) {
		String base = contenidoBase(b.getHashAnterior(), b);
		if (sha256(base + sufijoNuevo(b)).equals(b.getHash())) {
			return true;
		}
		if (b.getHoraCliente() == null && b.getRecurso() == null && b.getIdRecurso() == null) {
			return sha256(base).equals(b.getHash());
		}
		return false;
	}

	private static String contenidoBase(String anterior, Bitacora b) {
		return String.join("|",
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
	}

	private static String sufijoNuevo(Bitacora b) {
		return "|" + String.valueOf(b.getHoraCliente())
				+ "|" + String.valueOf(b.getRecurso())
				+ "|" + String.valueOf(b.getIdRecurso());
	}

	private static String sha256(String contenido) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(contenido.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception ex) {
			throw new IllegalStateException("SHA-256 no disponible", ex);
		}
	}

	/**
	 * El segmento que sigue a /empresas/{id} es el recurso, y el ultimo numero despues de ese
	 * id de empresa es el id del recurso. /api/empresas/3/ventas/15 -> venta, 15.
	 */
	public static RecursoRuta recursoDesdeRuta(String ruta) {
		if (ruta == null || ruta.isBlank()) {
			return new RecursoRuta(null, null);
		}
		String path = ruta;
		int corte = indexOfAny(path, '?', '#');
		if (corte >= 0) {
			path = path.substring(0, corte);
		}
		String[] raw = path.split("/");
		List<String> partes = new ArrayList<>();
		for (String parte : raw) {
			if (!parte.isBlank()) {
				partes.add(parte);
			}
		}
		int i = 0;
		if (!partes.isEmpty() && partes.get(0).equalsIgnoreCase("api")) {
			i = 1;
		}
		if (i + 1 < partes.size()
				&& partes.get(i).equalsIgnoreCase("empresas")
				&& partes.get(i + 1).matches("\\d+")) {
			if (i + 2 >= partes.size()) {
				return new RecursoRuta("empresa", partes.get(i + 1));
			}
			i += 2;
		}
		String recurso = null;
		String idRecurso = null;
		for (int j = i; j < partes.size(); j++) {
			String segmento = partes.get(j);
			if (segmento.matches("\\d+")) {
				idRecurso = segmento;
			} else if (recurso == null) {
				recurso = singular(segmento);
			}
		}
		return new RecursoRuta(recurso, idRecurso);
	}

	private static LocalDateTime parseHoraCliente(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String texto = raw.trim();
		try {
			Instant instant = Instant.parse(texto);
			return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).truncatedTo(ChronoUnit.MILLIS);
		} catch (DateTimeParseException ignored) {
			// El header puede traer offset u hora local sin zona.
		}
		try {
			OffsetDateTime conZona = OffsetDateTime.parse(texto);
			return LocalDateTime.ofInstant(conZona.toInstant(), ZoneId.systemDefault()).truncatedTo(ChronoUnit.MILLIS);
		} catch (DateTimeParseException ignored) {
			// Sigue el intento sin zona.
		}
		try {
			return LocalDateTime.parse(texto).truncatedTo(ChronoUnit.MILLIS);
		} catch (DateTimeParseException ignored) {
			return null;
		}
	}

	private static int indexOfAny(String value, char... chars) {
		int found = -1;
		for (char c : chars) {
			int at = value.indexOf(c);
			if (at >= 0 && (found < 0 || at < found)) {
				found = at;
			}
		}
		return found;
	}

	private static String singular(String segmento) {
		String palabra = segmento.toLowerCase(Locale.ROOT);
		int guion = palabra.indexOf('-');
		if (guion > 0) {
			return singularPalabra(palabra.substring(0, guion)) + palabra.substring(guion);
		}
		return singularPalabra(palabra);
	}

	private static String singularPalabra(String palabra) {
		if (palabra.length() > 4 && palabra.endsWith("es")) {
			char anterior = palabra.charAt(palabra.length() - 3);
			if (CONSONANTES_CON_ES.indexOf(anterior) >= 0) {
				return palabra.substring(0, palabra.length() - 2);
			}
		}
		if (palabra.length() > 3 && palabra.endsWith("s")) {
			return palabra.substring(0, palabra.length() - 1);
		}
		return palabra;
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
