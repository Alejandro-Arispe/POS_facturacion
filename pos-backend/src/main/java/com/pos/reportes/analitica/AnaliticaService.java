package com.pos.reportes.analitica;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;
import com.pos.common.jdbc.UtcTimestamps;
import com.pos.common.security.SecurityUtils;
import com.pos.empresas.domain.Empresa;
import com.pos.empresas.repo.ConfiguracionSistemaRepository;

/**
 * Puerta de entrada al microservicio de analitica (Python). Valida el acceso, extrae las ventas de la
 * empresa (periodo actual + periodo anterior para comparar) y delega los calculos al microservicio.
 */
@Service
public class AnaliticaService {

	private static final String SQL_LINEAS = """
			select v.id_venta as sale_id,
			       v.fecha as fecha,
			       s.nombre as branch,
			       p.nombre as product,
			       cp.nombre as category,
			       d.cantidad as quantity,
			       d.total as line_total,
			       v.total as sale_total
			  from detalle_venta d
			  join venta v on v.id_venta = d.id_venta
			  join caja_sesion cs on cs.id_caja_sesion = v.id_caja_sesion
			  join caja ca on ca.id_caja = cs.id_caja
			  join sucursal s on s.id_sucursal = ca.id_sucursal
			  join producto p on p.id_producto = d.id_producto
			  left join subcategoria_producto sp on sp.id_subcategoria = p.id_subcategoria
			  left join categoria_producto cp on cp.id_categoria_producto = sp.id_categoria_producto
			 where s.id_empresa = :idEmpresa and p.id_empresa=:idEmpresa
			   and v.fecha >= :desde
			   and v.fecha < :hasta
			   and upper(coalesce(v.estado, '')) not in ('ANULADA', 'CANCELADA')
			""";

	/** Pagos de cada venta: el reparto por metodo de pago usa lo pagado con cada uno (igual que los reportes). */
	private static final String SQL_PAGOS = """
			select v.id_venta as sale_id, mp.nombre as method, vp.monto as amount
			  from venta_pago vp
			  join metodo_pago mp on mp.id_metodo_pago = vp.id_metodo_pago
			  join venta v on v.id_venta = vp.id_venta
			  join caja_sesion cs on cs.id_caja_sesion = v.id_caja_sesion
			  join caja ca on ca.id_caja = cs.id_caja
			  join sucursal s on s.id_sucursal = ca.id_sucursal
			 where s.id_empresa = :idEmpresa
			   and v.fecha >= :desde
			   and v.fecha < :hasta
			   and upper(coalesce(v.estado, '')) not in ('ANULADA', 'CANCELADA')
			""";

	private static final String SQL_STOCK = """
			select p.nombre as product, s.nombre as branch, st.cantidad as quantity, st.stock_minimo as min_stock
			  from stock st
			  join producto p on p.id_producto = st.id_producto
			  join sucursal s on s.id_sucursal = st.id_sucursal
			 where p.id_empresa = :idEmpresa and s.id_empresa=:idEmpresa
			""";

	private final NamedParameterJdbcTemplate jdbc;
	private final EmpresaAccess empresaAccess;
    private final com.pos.common.access.DataReadPolicy dataPolicy;
	private final ConfiguracionSistemaRepository configuracionRepository;
	private final RestClient restClient;
	private final String apiKey;

	public AnaliticaService(
			NamedParameterJdbcTemplate jdbc,
			EmpresaAccess empresaAccess, com.pos.common.access.DataReadPolicy dataPolicy,
			ConfiguracionSistemaRepository configuracionRepository,
			@Value("${app.analytics.url:http://localhost:8001}") String analyticsUrl,
			@Value("${app.analytics.api-key:}") String apiKey) {
		this.jdbc = jdbc;
		this.empresaAccess = empresaAccess; this.dataPolicy=dataPolicy;
		this.configuracionRepository = configuracionRepository;
		this.apiKey = apiKey;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(5_000);
		factory.setReadTimeout(30_000);
		this.restClient = RestClient.builder().baseUrl(analyticsUrl).requestFactory(factory).build();
	}

	@Transactional(readOnly = true)
	public Map<String, Object> analizar(Integer idEmpresa, Integer dias, Integer idSucursal) {
		var scope=dataPolicy.require(SecurityUtils.currentUser(),idEmpresa,idSucursal,"dashboard:read","sale:read","inventory:read");
        dataPolicy.productFields(scope,"nombre","id_subcategoria");
        empresaAccess.requirePlanFeature(SecurityUtils.currentUser(),idEmpresa,"reportes_avanzados");
		Empresa empresa = empresaAccess.requireEmpresa(SecurityUtils.currentUser(), idEmpresa);
		int periodo = dias == null || dias <= 0 ? 30 : Math.min(dias, 365);
		LocalDate fin = LocalDate.now();
		LocalDate inicio = fin.minusDays(periodo - 1L);
		LocalDate inicioComparacion = inicio.minusDays(periodo);

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("idEmpresa", idEmpresa)
				.addValue("desde", UtcTimestamps.parametro(inicioComparacion.atStartOfDay()))
				.addValue("hasta", UtcTimestamps.parametro(fin.plusDays(1).atStartOfDay()));
		String sqlLineas = SQL_LINEAS;
		String sqlStock = SQL_STOCK;
		String sqlPagos = SQL_PAGOS;
		if (idSucursal != null) {
			empresaAccess.requireSucursal(SecurityUtils.currentUser(), idEmpresa, idSucursal);
			params.addValue("idSucursal", idSucursal);
			sqlLineas += " and s.id_sucursal = :idSucursal";
			sqlStock += " and s.id_sucursal = :idSucursal";
			sqlPagos += " and s.id_sucursal = :idSucursal";
		}

		List<Map<String, Object>> lineas = jdbc.query(sqlLineas, params, (rs, i) -> {
			Map<String, Object> linea = new LinkedHashMap<>();
			linea.put("sale_id", rs.getInt("sale_id"));
			linea.put("timestamp", UtcTimestamps.leer(rs, "fecha").toString());
			linea.put("branch", rs.getString("branch"));
			linea.put("product", rs.getString("product"));
			linea.put("category", rs.getString("category"));
			linea.put("quantity", rs.getBigDecimal("quantity"));
			linea.put("line_total", rs.getBigDecimal("line_total"));
			linea.put("sale_total", rs.getBigDecimal("sale_total"));
			return linea;
		});
		List<Map<String, Object>> pagos = jdbc.query(sqlPagos, params, (rs, i) -> {
			Map<String, Object> pago = new LinkedHashMap<>();
			pago.put("sale_id", rs.getInt("sale_id"));
			pago.put("method", rs.getString("method"));
			pago.put("amount", rs.getBigDecimal("amount"));
			return pago;
		});
		List<Map<String, Object>> stock = jdbc.query(sqlStock, params, (rs, i) -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("product", rs.getString("product"));
			item.put("branch", rs.getString("branch"));
			item.put("quantity", rs.getBigDecimal("quantity"));
			item.put("min_stock", rs.getBigDecimal("min_stock"));
			return item;
		});

		String moneda = configuracionRepository.findByEmpresa_IdEmpresa(idEmpresa)
				.map(c -> c.getMoneda())
				.orElse("BOB");

		Map<String, Object> solicitud = new LinkedHashMap<>();
		solicitud.put("company", empresa.getNombre());
		solicitud.put("currency", moneda);
		solicitud.put("start", inicio.toString());
		solicitud.put("end", fin.toString());
		solicitud.put("forecast_days", 14);
		solicitud.put("lines", lineas);
		solicitud.put("payments", pagos);
		solicitud.put("stock", stock);

		try {
			RestClient.RequestBodySpec request = restClient.post()
					.uri("/analyze")
					.contentType(MediaType.APPLICATION_JSON);
			if (apiKey != null && !apiKey.isBlank()) {
				request = request.header("X-API-Key", apiKey);
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> respuesta = request.body(solicitud).retrieve().body(Map.class);
			return respuesta;
		} catch (RestClientException ex) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
					"El servicio de analitica no esta disponible en este momento. Intenta nuevamente en unos minutos.");
		}
	}
}
