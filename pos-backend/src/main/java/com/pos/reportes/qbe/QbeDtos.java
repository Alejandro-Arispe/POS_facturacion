package com.pos.reportes.qbe;

import java.util.List;

public final class QbeDtos {

	private QbeDtos() {
	}

	public record CampoInfo(String codigo, String etiqueta, String tipo, List<String> operadores) {
	}

	public record FuenteInfo(
			String codigo,
			String nombre,
			String descripcion,
			List<CampoInfo> campos,
			List<String> camposPorDefecto) {
	}

	public record Condicion(String campo, String operador, String valor, String valor2) {
	}

	public record Agregado(String campo, String funcion) {
	}

	public record Orden(String campo, String direccion) {
	}

	public record Consulta(
			String fuente,
			List<String> campos,
			List<Condicion> condiciones,
			List<String> agrupar,
			List<Agregado> agregados,
			Orden orden,
			Integer limite) {
	}

	public record Columna(String codigo, String etiqueta, String tipo) {
	}

	public record Resultado(
			List<Columna> columnas,
			List<List<Object>> filas,
			int totalFilas,
			boolean truncado,
			String descripcion) {
	}
}
