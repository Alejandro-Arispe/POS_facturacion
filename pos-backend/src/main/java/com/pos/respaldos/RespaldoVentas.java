package com.pos.respaldos;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;

/**
 * Lee las ventas de un periodo para guardarlas en el respaldo como copia de consulta.
 * No se usan para restaurar: ventas y facturas son historial y documentos fiscales.
 */
@Component
public class RespaldoVentas {

    /** Ventas validas del periodo (excluye borradores); las anuladas se incluyen con su estado. */
    private static final String FILTRO = """
            from venta v
            join caja_sesion cs on cs.id_caja_sesion = v.id_caja_sesion
            join caja c on c.id_caja = cs.id_caja
            join sucursal s on s.id_sucursal = c.id_sucursal
            where s.id_empresa = :idEmpresa
              and (:idSucursal = -1 or s.id_sucursal = :idSucursal)
              and v.fecha >= :desde and v.fecha < :hastaExclusivo
            """;

    private final EntityManager entityManager;

    public RespaldoVentas(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public RespaldoDocumento.VentasHistorial leer(Integer idEmpresa, Integer idSucursal, LocalDate desde, LocalDate hasta) {
        List<Map<String, Object>> ventas = filas("""
                select v.id_venta, v.fecha, s.nombre as sucursal, c.nombre as caja, tv.nombre as tipo_venta,
                       v.estado, v.subtotal, v.descuento_total, v.total, v.id_cliente,
                       u.email as usuario, v.fecha_anulacion, v.motivo_anulacion
                """ + FILTRO.replace("from venta v", """
                from venta v
                left join tipo_venta tv on tv.id_tipo_venta = v.id_tipo_venta
                left join usuario u on u.id_usuario = v.id_usuario""") + " order by v.fecha, v.id_venta",
                idEmpresa, idSucursal, desde, hasta);
        List<Map<String, Object>> detalles = filas("""
                select d.id_venta, d.id_detalle_venta, p.nombre as producto, d.cantidad, d.precio_unitario,
                       d.descuento, d.subtotal, d.total
                """ + FILTRO.replace("from venta v", """
                from detalle_venta d
                join venta v on v.id_venta = d.id_venta
                left join producto p on p.id_producto = d.id_producto""") + " order by d.id_venta, d.id_detalle_venta",
                idEmpresa, idSucursal, desde, hasta);
        List<Map<String, Object>> pagos = filas("""
                select vp.id_venta, mp.nombre as metodo_pago, vp.monto, vp.fecha,
                       vp.cheque_banco, vp.cheque_numero, vp.cheque_fecha_emision
                """ + FILTRO.replace("from venta v", """
                from venta_pago vp
                join venta v on v.id_venta = vp.id_venta
                left join metodo_pago mp on mp.id_metodo_pago = vp.id_metodo_pago""") + " order by vp.id_venta",
                idEmpresa, idSucursal, desde, hasta);
        // Sin XML ni PDF: son pesados y se pueden volver a descargar desde el sistema.
        List<Map<String, Object>> facturas = filas("""
                select f.id_venta, f.id_factura, f.numero_factura, f.fecha_emision, f.nit_emisor,
                       f.codigo_tipo_documento, f.nit_cliente, f.razon_social_cliente, f.monto_total, f.iva,
                       f.estado_siat, f.cuf, f.fecha_anulacion, f.correo_estado
                """ + FILTRO.replace("from venta v", """
                from factura f
                join venta v on v.id_venta = f.id_venta""") + " order by f.numero_factura",
                idEmpresa, idSucursal, desde, hasta);
        List<Map<String, Object>> sesiones = filas("""
                select cs.id_caja_sesion, s.nombre as sucursal, c.nombre as caja, u.email as usuario,
                       cs.estado, cs.fecha_apertura, cs.fecha_cierre, cs.monto_inicial, cs.monto_final
                from caja_sesion cs
                join caja c on c.id_caja = cs.id_caja
                join sucursal s on s.id_sucursal = c.id_sucursal
                left join usuario u on u.id_usuario = cs.id_usuario
                where s.id_empresa = :idEmpresa
                  and (:idSucursal = -1 or s.id_sucursal = :idSucursal)
                  and cs.fecha_apertura < :hastaExclusivo
                  and (cs.fecha_cierre is null or cs.fecha_cierre >= :desde)
                order by cs.fecha_apertura
                """, idEmpresa, idSucursal, desde, hasta);
        List<Map<String, Object>> cierres = filas("""
                select d.id_caja_sesion, mp.nombre as metodo_pago, d.monto_esperado, d.monto_real,
                       d.diferencia, d.observacion
                from caja_cierre_detalle d
                join caja_sesion cs on cs.id_caja_sesion = d.id_caja_sesion
                join caja c on c.id_caja = cs.id_caja
                join sucursal s on s.id_sucursal = c.id_sucursal
                left join metodo_pago mp on mp.id_metodo_pago = d.id_metodo_pago
                where s.id_empresa = :idEmpresa
                  and (:idSucursal = -1 or s.id_sucursal = :idSucursal)
                  and cs.fecha_apertura < :hastaExclusivo
                  and (cs.fecha_cierre is null or cs.fecha_cierre >= :desde)
                order by d.id_caja_sesion
                """, idEmpresa, idSucursal, desde, hasta);

        BigDecimal total = ventas.stream()
                .filter(v -> !"ANULADA".equalsIgnoreCase(String.valueOf(v.get("estado"))))
                .map(v -> v.get("total") instanceof BigDecimal b ? b : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RespaldoDocumento.VentasHistorial(desde, hasta, ventas.size(), total,
                ventas, detalles, pagos, facturas, sesiones, cierres);
    }

    private List<Map<String, Object>> filas(String sql, Integer idEmpresa, Integer idSucursal,
            LocalDate desde, LocalDate hasta) {
        Query query = entityManager.createNativeQuery(sql, Tuple.class)
                .setParameter("idEmpresa", idEmpresa)
                .setParameter("idSucursal", idSucursal == null ? -1 : idSucursal)
                .setParameter("desde", desde.atStartOfDay())
                .setParameter("hastaExclusivo", hasta.plusDays(1).atStartOfDay());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object raw : query.getResultList()) {
            Tuple tuple = (Tuple) raw;
            Map<String, Object> fila = new LinkedHashMap<>();
            for (TupleElement<?> e : tuple.getElements()) {
                Object valor = tuple.get(e);
                if (valor instanceof Timestamp ts) {
                    valor = ts.toLocalDateTime().withNano(0);
                } else if (valor instanceof java.sql.Date d) {
                    valor = d.toLocalDate();
                }
                fila.put(e.getAlias(), valor);
            }
            out.add(fila);
        }
        return out;
    }
}
