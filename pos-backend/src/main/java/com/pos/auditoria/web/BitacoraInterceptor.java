package com.pos.auditoria.web;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.pos.auditoria.service.BitacoraService;
import com.pos.common.security.SecurityUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Registra en la bitacora toda operacion de escritura sobre la API (crear, actualizar, eliminar).
 * El login se registra aparte en UsuarioService porque necesita distinguir exito y fallo.
 */
@Component
public class BitacoraInterceptor implements HandlerInterceptor {

	private static final Set<String> METODOS_ESCRITURA = Set.of("POST", "PUT", "PATCH", "DELETE");
	private static final Set<String> RUTAS_EXCLUIDAS = Set.of("/api/auth/login");
	private static final Pattern EMPRESA_EN_RUTA = Pattern.compile(
			"/(?:empresas|categorias-cliente|reportes)/(\\d+)(?:/|$)");
	private static final List<String[]> MODULOS = List.of(
			new String[] {"/auth/", "AUTENTICACION"},
			new String[] {"/superadmin", "SUPERADMIN"},
			new String[] {"/pagos", "SUSCRIPCIONES"},
			new String[] {"/inventario", "INVENTARIO"},
			new String[] {"/productos", "PRODUCTOS"},
			new String[] {"/categorias-cliente", "CLIENTES"},
			new String[] {"/categorias", "PRODUCTOS"},
			new String[] {"/subcategorias", "PRODUCTOS"},
			new String[] {"/ventas", "VENTAS"},
			new String[] {"/pedidos", "PEDIDOS"},
			new String[] {"/cajas", "CAJAS"},
			new String[] {"/caja", "CAJAS"},
			new String[] {"/clientes", "CLIENTES"},
			new String[] {"/sucursales", "SUCURSALES"},
			new String[] {"/invitar", "USUARIOS"},
			new String[] {"/personal", "USUARIOS"},
			new String[] {"/roles", "ROLES"},
			new String[] {"/reportes", "REPORTES"},
			new String[] {"/configuracion-sistema", "CONFIGURACION"},
			new String[] {"/respaldos", "RESPALDOS"},
			new String[] {"/empresas", "EMPRESAS"});

	private final BitacoraService bitacoraService;

	public BitacoraInterceptor(BitacoraService bitacoraService) {
		this.bitacoraService = bitacoraService;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
		String metodo = request.getMethod().toUpperCase(Locale.ROOT);
		String ruta = request.getRequestURI();
		if (!METODOS_ESCRITURA.contains(metodo) || !ruta.startsWith("/api/") || RUTAS_EXCLUIDAS.contains(ruta)) {
			return;
		}
		int estado = response.getStatus();
		String accion = switch (metodo) {
			case "POST" -> "CREAR";
			case "DELETE" -> "ELIMINAR";
			default -> "ACTUALIZAR";
		};
		if (estado >= 400) {
			accion = accion + "_FALLIDO";
		}
		String modulo = modulo(ruta);
		bitacoraService.registrar(new BitacoraService.Evento(
				SecurityUtils.currentUserOrNull(),
				null,
				idEmpresa(ruta),
				accion,
				modulo,
				metodo,
				ruta,
				estado,
				descripcion(accion, modulo, estado)));
	}

	private static Integer idEmpresa(String ruta) {
		Matcher m = EMPRESA_EN_RUTA.matcher(ruta);
		return m.find() ? Integer.valueOf(m.group(1)) : null;
	}

	private static String modulo(String ruta) {
		String sinPrefijo = ruta.substring("/api".length());
		for (String[] par : MODULOS) {
			if (sinPrefijo.contains(par[0])) {
				return par[1];
			}
		}
		return "GENERAL";
	}

	private static String descripcion(String accion, String modulo, int estado) {
		String verbo = switch (accion.replace("_FALLIDO", "")) {
			case "CREAR" -> "Registro/creacion";
			case "ELIMINAR" -> "Eliminacion";
			default -> "Actualizacion";
		};
		return verbo + " en " + modulo.toLowerCase(Locale.ROOT)
				+ (estado >= 400 ? " rechazada (HTTP " + estado + ")" : " realizada");
	}
}
