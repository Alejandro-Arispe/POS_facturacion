package com.pos.reportes.qbe;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;
import com.pos.common.jdbc.UtcTimestamps;
import com.pos.common.security.SecurityUtils;
import com.pos.reportes.qbe.QbeCatalogo.Campo;
import com.pos.reportes.qbe.QbeCatalogo.Fuente;
import com.pos.reportes.qbe.QbeCatalogo.Tipo;

/**
 * Ejecuta consultas por ejemplo (QBE). Construye el SQL solo con expresiones del catalogo y parametros
 * enlazados, y siempre filtra por la empresa del usuario.
 */
@Service
public class QbeService {

	private static final int LIMITE_MAXIMO = 1000;
	private static final Set<String> FUNCIONES = Set.of("SUMA", "CONTEO", "PROMEDIO", "MAXIMO", "MINIMO");

	private final NamedParameterJdbcTemplate jdbc;
	private final EmpresaAccess empresaAccess;

	public QbeService(NamedParameterJdbcTemplate jdbc, EmpresaAccess empresaAccess) {
		this.jdbc = jdbc;
		this.empresaAccess = empresaAccess;
	}

	public List<QbeDtos.FuenteInfo> fuentes(Integer idEmpresa) {
		empresaAccess.requirePermiso(SecurityUtils.currentUser(), idEmpresa, "REPORTE_GENERAR");
		empresaAccess.requirePlanFeature(SecurityUtils.currentUser(), idEmpresa, "reportes_avanzados");
		return QbeCatalogo.FUENTES.values().stream()
				.map(f -> new QbeDtos.FuenteInfo(
						f.codigo(),
						f.nombre(),
						f.descripcion(),
						f.campos().stream()
								.map(c -> new QbeDtos.CampoInfo(
										c.codigo(), c.etiqueta(), c.tipo().name(), QbeCatalogo.OPERADORES.get(c.tipo())))
								.toList(),
						f.camposPorDefecto()))
				.toList();
	}

	@Transactional(readOnly = true)
	public QbeDtos.Resultado ejecutar(Integer idEmpresa, QbeDtos.Consulta consulta) {
		empresaAccess.requirePermiso(SecurityUtils.currentUser(), idEmpresa, "REPORTE_GENERAR");
		empresaAccess.requirePlanFeature(SecurityUtils.currentUser(), idEmpresa, "reportes_avanzados");
		if (consulta == null || consulta.fuente() == null) {
			throw ApiException.badRequest("Debe elegir una fuente de datos.");
		}
		Fuente fuente = QbeCatalogo.FUENTES.get(consulta.fuente());
		if (fuente == null) {
			throw ApiException.badRequest("Fuente de datos desconocida: " + consulta.fuente());
		}

		MapSqlParameterSource params = new MapSqlParameterSource("idEmpresa", idEmpresa);
		List<String> seleccion = new ArrayList<>();
		List<QbeDtos.Columna> columnas = new ArrayList<>();
		List<String> agrupacion = new ArrayList<>();
		List<String> descripcion = new ArrayList<>();

		boolean agrupado = consulta.agrupar() != null && !consulta.agrupar().isEmpty();
		if (agrupado) {
			for (String codigo : new LinkedHashSet<>(consulta.agrupar())) {
				Campo campo = requireCampo(fuente, codigo);
				String expr = expresionAgrupable(campo);
				seleccion.add(expr + " as c" + seleccion.size());
				agrupacion.add(expr);
				columnas.add(new QbeDtos.Columna(campo.codigo(), campo.etiqueta(),
						campo.tipo() == Tipo.FECHA ? Tipo.TEXTO.name() : campo.tipo().name()));
			}
			List<QbeDtos.Agregado> agregados = consulta.agregados() == null || consulta.agregados().isEmpty()
					? List.of(new QbeDtos.Agregado(null, "CONTEO"))
					: consulta.agregados();
			for (QbeDtos.Agregado agregado : agregados) {
				String funcion = agregado.funcion() == null ? "" : agregado.funcion().toUpperCase(Locale.ROOT);
				if (!FUNCIONES.contains(funcion)) {
					throw ApiException.badRequest("Funcion de agregacion no valida: " + agregado.funcion());
				}
				if ("CONTEO".equals(funcion)) {
					seleccion.add("count(*) as c" + seleccion.size());
					columnas.add(new QbeDtos.Columna("conteo", "Cantidad de registros", Tipo.NUMERO.name()));
					continue;
				}
				Campo campo = requireCampo(fuente, agregado.campo());
				if (campo.tipo() != Tipo.NUMERO) {
					throw ApiException.badRequest("Solo se pueden totalizar campos numericos: " + campo.etiqueta());
				}
				String sqlFuncion = switch (funcion) {
					case "SUMA" -> "sum";
					case "PROMEDIO" -> "avg";
					case "MAXIMO" -> "max";
					default -> "min";
				};
				seleccion.add(sqlFuncion + "(" + campo.expresion() + ") as c" + seleccion.size());
				columnas.add(new QbeDtos.Columna(
						funcion.toLowerCase(Locale.ROOT) + "_" + campo.codigo(),
						etiquetaFuncion(funcion) + " de " + campo.etiqueta().toLowerCase(Locale.ROOT),
						Tipo.NUMERO.name()));
			}
		} else {
			List<String> campos = consulta.campos() == null || consulta.campos().isEmpty()
					? fuente.camposPorDefecto()
					: consulta.campos();
			for (String codigo : new LinkedHashSet<>(campos)) {
				Campo campo = requireCampo(fuente, codigo);
				seleccion.add(campo.expresion() + " as c" + seleccion.size());
				columnas.add(new QbeDtos.Columna(campo.codigo(), campo.etiqueta(), campo.tipo().name()));
			}
		}

		List<String> where = new ArrayList<>();
		where.add(fuente.filtroEmpresa());
		if (consulta.condiciones() != null) {
			int i = 0;
			for (QbeDtos.Condicion condicion : consulta.condiciones()) {
				if (condicion == null || condicion.campo() == null || condicion.campo().isBlank()) {
					continue;
				}
				Campo campo = requireCampo(fuente, condicion.campo());
				where.add(condicionSql(campo, condicion, "p" + i, params));
				descripcion.add(campo.etiqueta() + " " + condicion.operador() + " " + nvl(condicion.valor())
						+ (condicion.valor2() != null && !condicion.valor2().isBlank() ? " y " + condicion.valor2() : ""));
				i++;
			}
		}

		StringBuilder sql = new StringBuilder("select ")
				.append(String.join(", ", seleccion))
				.append(" from ").append(fuente.desde())
				.append(" where ").append(String.join(" and ", where));
		if (agrupado) {
			sql.append(" group by ").append(String.join(", ", agrupacion));
		}
		sql.append(ordenSql(consulta.orden(), columnas));
		int limite = consulta.limite() == null || consulta.limite() <= 0
				? 200
				: Math.min(consulta.limite(), LIMITE_MAXIMO);
		sql.append(" limit ").append(limite + 1);

		List<List<Object>> filas = jdbc.query(sql.toString(), params, (rs, rowNum) -> {
			List<Object> fila = new ArrayList<>(columnas.size());
			for (int c = 0; c < columnas.size(); c++) {
				Object valor = rs.getObject(c + 1);
				if (valor instanceof Timestamp) {
					valor = UtcTimestamps.leer(rs, c + 1).toString();
				} else if (valor instanceof java.sql.Date d) {
					valor = d.toLocalDate().toString();
				}
				fila.add(valor);
			}
			return fila;
		});
		boolean truncado = filas.size() > limite;
		if (truncado) {
			filas = filas.subList(0, limite);
		}
		String resumen = fuente.nombre()
				+ (descripcion.isEmpty() ? "" : " donde " + String.join(", ", descripcion))
				+ (agrupado ? " agrupado por " + String.join(", ", consulta.agrupar()) : "");
		return new QbeDtos.Resultado(columnas, filas, filas.size(), truncado, resumen);
	}

	private String condicionSql(Campo campo, QbeDtos.Condicion condicion, String nombre, MapSqlParameterSource params) {
		String operador = condicion.operador() == null ? "igual" : condicion.operador();
		if (!QbeCatalogo.OPERADORES.get(campo.tipo()).contains(operador)) {
			throw ApiException.badRequest("Operador '" + operador + "' no valido para " + campo.etiqueta());
		}
		String expr = campo.expresion();
		switch (campo.tipo()) {
			case TEXTO -> {
				switch (operador) {
					case "vacio":
						return "(" + expr + " is null or " + expr + " = '')";
					case "no_vacio":
						return "(" + expr + " is not null and " + expr + " <> '')";
					case "contiene":
						params.addValue(nombre, "%" + requireValor(condicion.valor(), campo).toLowerCase(Locale.ROOT) + "%");
						return "lower(" + expr + ") like :" + nombre;
					case "empieza":
						params.addValue(nombre, requireValor(condicion.valor(), campo).toLowerCase(Locale.ROOT) + "%");
						return "lower(" + expr + ") like :" + nombre;
					case "distinto":
						params.addValue(nombre, requireValor(condicion.valor(), campo).toLowerCase(Locale.ROOT));
						return "(" + expr + " is null or lower(" + expr + ") <> :" + nombre + ")";
					default:
						params.addValue(nombre, requireValor(condicion.valor(), campo).toLowerCase(Locale.ROOT));
						return "lower(" + expr + ") = :" + nombre;
				}
			}
			case NUMERO -> {
				params.addValue(nombre, numero(condicion.valor(), campo));
				if ("entre".equals(operador)) {
					params.addValue(nombre + "b", numero(condicion.valor2(), campo));
					return expr + " between :" + nombre + " and :" + nombre + "b";
				}
				return expr + " " + simbolo(operador) + " :" + nombre;
			}
			case FECHA -> {
				LocalDate desde = fecha(condicion.valor(), campo);
				switch (operador) {
					case "mayor_igual":
						params.addValue(nombre, UtcTimestamps.parametro(desde.atStartOfDay()));
						return expr + " >= :" + nombre;
					case "menor_igual":
						params.addValue(nombre, UtcTimestamps.parametro(desde.plusDays(1).atStartOfDay()));
						return expr + " < :" + nombre;
					case "entre": {
						LocalDate hasta = fecha(condicion.valor2(), campo);
						params.addValue(nombre, UtcTimestamps.parametro(desde.atStartOfDay()));
						params.addValue(nombre + "b", UtcTimestamps.parametro(hasta.plusDays(1).atStartOfDay()));
						return "(" + expr + " >= :" + nombre + " and " + expr + " < :" + nombre + "b)";
					}
					default:
						params.addValue(nombre, UtcTimestamps.parametro(desde.atStartOfDay()));
						params.addValue(nombre + "b", UtcTimestamps.parametro(desde.plusDays(1).atStartOfDay()));
						return "(" + expr + " >= :" + nombre + " and " + expr + " < :" + nombre + "b)";
				}
			}
			default -> {
				params.addValue(nombre, Boolean.parseBoolean(nvl(condicion.valor()).trim()));
				return expr + " = :" + nombre;
			}
		}
	}

	private String ordenSql(QbeDtos.Orden orden, List<QbeDtos.Columna> columnas) {
		if (orden == null || orden.campo() == null || orden.campo().isBlank()) {
			return "";
		}
		for (int i = 0; i < columnas.size(); i++) {
			if (columnas.get(i).codigo().equals(orden.campo())) {
				String dir = "desc".equalsIgnoreCase(orden.direccion()) ? " desc" : " asc";
				return " order by " + (i + 1) + dir;
			}
		}
		return "";
	}

	/** Las fechas se agrupan por dia para que el agrupamiento tenga sentido. */
	private String expresionAgrupable(Campo campo) {
		return campo.tipo() == Tipo.FECHA ? "cast(" + campo.expresion() + " as date)" : campo.expresion();
	}

	private Campo requireCampo(Fuente fuente, String codigo) {
		Campo campo = codigo == null ? null : fuente.campo(codigo);
		if (campo == null) {
			throw ApiException.badRequest("Campo no valido para " + fuente.nombre() + ": " + codigo);
		}
		return campo;
	}

	private static String requireValor(String valor, Campo campo) {
		if (valor == null || valor.isBlank()) {
			throw ApiException.badRequest("Falta el valor para " + campo.etiqueta());
		}
		return valor.trim();
	}

	private static BigDecimal numero(String valor, Campo campo) {
		try {
			return new BigDecimal(requireValor(valor, campo).replace(',', '.'));
		} catch (NumberFormatException ex) {
			throw ApiException.badRequest("Valor numerico invalido para " + campo.etiqueta() + ": " + valor);
		}
	}

	private static LocalDate fecha(String valor, Campo campo) {
		try {
			return LocalDate.parse(requireValor(valor, campo));
		} catch (Exception ex) {
			throw ApiException.badRequest("Fecha invalida para " + campo.etiqueta() + " (use AAAA-MM-DD): " + valor);
		}
	}

	private static String simbolo(String operador) {
		return Map.of(
				"igual", "=",
				"distinto", "<>",
				"mayor", ">",
				"mayor_igual", ">=",
				"menor", "<",
				"menor_igual", "<=").getOrDefault(operador, "=");
	}

	private static String etiquetaFuncion(String funcion) {
		return switch (funcion) {
			case "SUMA" -> "Suma";
			case "PROMEDIO" -> "Promedio";
			case "MAXIMO" -> "Máximo";
			default -> "Mínimo";
		};
	}

	private static String nvl(String value) {
		return value == null ? "" : value;
	}
}
