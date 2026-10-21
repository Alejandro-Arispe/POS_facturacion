package com.pos.auditoria;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.pos.auditoria.domain.Bitacora;

/**
 * Copia append-only de cada evento, fuera de la base. El hash de la linea es el de la fila.
 * No hay borrado ni actualizacion de este archivo.
 */
@Component
public class CajaNegraWriter {

	private static final Path ARCHIVO = Path.of("caja-negra", "bitacora.log");

	public void anexar(Bitacora registro) {
		try {
			String linea = lineaJson(registro) + "\n";
			synchronized (this) {
				Path carpeta = ARCHIVO.getParent();
				if (carpeta != null) {
					Files.createDirectories(carpeta);
				}
				Files.write(
						ARCHIVO,
						linea.getBytes(StandardCharsets.UTF_8),
						StandardOpenOption.CREATE,
						StandardOpenOption.WRITE,
						StandardOpenOption.APPEND);
			}
		} catch (Exception ex) {
			System.out.println("[CAJA-NEGRA] No se pudo anexar el evento: " + ex.getMessage());
		}
	}

	private static String lineaJson(Bitacora b) {
		return "{"
				+ "\"id_bitacora\":" + num(b.getIdBitacora()) + ","
				+ "\"fecha\":" + texto(b.getFecha()) + ","
				+ "\"hora_cliente\":" + texto(b.getHoraCliente()) + ","
				+ "\"hora_servidor\":" + texto(b.getHoraServidor()) + ","
				+ "\"id_usuario\":" + num(b.getIdUsuario()) + ","
				+ "\"email_usuario\":" + texto(b.getEmailUsuario()) + ","
				+ "\"id_empresa\":" + num(b.getIdEmpresa()) + ","
				+ "\"accion\":" + texto(b.getAccion()) + ","
				+ "\"modulo\":" + texto(b.getModulo()) + ","
				+ "\"metodo\":" + texto(b.getMetodo()) + ","
				+ "\"ruta\":" + texto(b.getRuta()) + ","
				+ "\"estado_http\":" + num(b.getEstadoHttp()) + ","
				+ "\"ip\":" + texto(b.getIp()) + ","
				+ "\"user_agent\":" + texto(b.getUserAgent()) + ","
				+ "\"detalle\":" + texto(b.getDetalle()) + ","
				+ "\"recurso\":" + texto(b.getRecurso()) + ","
				+ "\"id_recurso\":" + texto(b.getIdRecurso()) + ","
				+ "\"hash_anterior\":" + texto(b.getHashAnterior()) + ","
				+ "\"hash\":" + texto(b.getHash())
				+ "}";
	}

	private static String num(Number value) {
		return value == null ? "null" : value.toString();
	}

	private static String texto(LocalDateTime value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	private static String texto(String value) {
		if (value == null) {
			return "null";
		}
		StringBuilder sb = new StringBuilder(value.length() + 2);
		sb.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		sb.append('"');
		return sb.toString();
	}
}
