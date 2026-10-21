package com.pos.websocket;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.empresas.domain.Empresa;
import com.pos.empresas.domain.Sucursal;
import com.pos.empresas.repo.EmpresaRepository;
import com.pos.empresas.repo.SucursalRepository;
import com.pos.inventario.domain.Stock;
import com.pos.inventario.repo.StockRepository;
import com.pos.productos.domain.Producto;
import com.pos.ventas.domain.Venta;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class AdminDashboardService {

    private final EmpresaRepository empresaRepository;
    private final SucursalRepository sucursalRepository;
    private final StockRepository stockRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public AdminDashboardService(
            EmpresaRepository empresaRepository,
            SucursalRepository sucursalRepository,
            StockRepository stockRepository) {
        this.empresaRepository = empresaRepository;
        this.sucursalRepository = sucursalRepository;
        this.stockRepository = stockRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> calcular(Integer idEmpresa, Integer idSucursal) {
        Empresa empresa = empresaRepository.findById(idEmpresa).orElse(null);
        if (empresa == null) {
            return Map.of("error", "Empresa no encontrada.", "id_empresa", idEmpresa);
        }
        LocalDateTime inicioHoy = LocalDate.now().atStartOfDay();
        LocalDateTime finHoy = LocalDate.now().atTime(LocalTime.MAX);
        LocalDateTime inicio30 = LocalDate.now().minusDays(29).atStartOfDay();

        List<Venta> ventasHoy = ventas(idEmpresa, idSucursal, inicioHoy, finHoy);
        double montoHoy = ventasHoy.stream().mapToDouble(v -> v.getTotal() == null ? 0 : v.getTotal().doubleValue()).sum();
        long cantidadHoy = ventasHoy.size();

        Long cajasAbiertas;
        if (idSucursal != null) {
            cajasAbiertas = entityManager.createQuery("""
                    select count(cs.idCajaSesion)
                    from CajaSesion cs
                    join cs.caja c
                    join c.sucursal s
                    where s.empresa.idEmpresa = :idEmpresa
                      and cs.estado = 'Abierto'
                      and s.idSucursal = :idSucursal
                    """, Long.class)
                    .setParameter("idEmpresa", idEmpresa)
                    .setParameter("idSucursal", idSucursal)
                    .getSingleResult();
        } else {
            cajasAbiertas = entityManager.createQuery("""
                    select count(cs.idCajaSesion)
                    from CajaSesion cs
                    join cs.caja c
                    join c.sucursal s
                    where s.empresa.idEmpresa = :idEmpresa
                      and cs.estado = 'Abierto'
                    """, Long.class)
                    .setParameter("idEmpresa", idEmpresa)
                    .getSingleResult();
        }

        List<Sucursal> sucursales = sucursalRepository.findByEmpresa_IdEmpresa(idEmpresa);
        int bajo = 0;
        int agotados = 0;
        for (Sucursal sucursal : sucursales) {
            if (idSucursal != null && !idSucursal.equals(sucursal.getIdSucursal())) {
                continue;
            }
            for (Stock stock : stockRepository.findByIdSucursal(sucursal.getIdSucursal())) {
                if (stock.getCantidad() == null || stock.getCantidad() <= 0) {
                    agotados++;
                } else if (stock.getStockMinimo() != null && stock.getCantidad() < stock.getStockMinimo()) {
                    bajo++;
                }
            }
        }

        Map<Integer, Integer> unidadesPorProducto = new LinkedHashMap<>();
        for (Venta venta : ventasHoy) {
            List<Object[]> dets = entityManager.createQuery(
                    "select d.idProducto, d.cantidad from DetalleVenta d where d.venta.idVenta = :id", Object[].class)
                    .setParameter("id", venta.getIdVenta())
                    .getResultList();
            for (Object[] d : dets) {
                Integer idProducto = (Integer) d[0];
                Integer cantidad = (Integer) d[1];
                unidadesPorProducto.merge(idProducto, cantidad == null ? 0 : cantidad, Integer::sum);
            }
        }
        Map<String, Object> productoEstrella = null;
        if (!unidadesPorProducto.isEmpty()) {
            Integer idTop = unidadesPorProducto.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (idTop != null) {
                Producto producto = entityManager.find(Producto.class, idTop);
                productoEstrella = Map.of(
                        "id_producto", idTop,
                        "nombre", producto == null ? "Producto " + idTop : producto.getNombre(),
                        "unidades", unidadesPorProducto.get(idTop));
            }
        }

        List<Venta> ventas30 = ventas(idEmpresa, idSucursal, inicio30, finHoy);
        Map<String, Double> porDia = new LinkedHashMap<>();
        for (Venta venta : ventas30) {
            if (venta.getFecha() == null) {
                continue;
            }
            String dia = venta.getFecha().toLocalDate().toString();
            porDia.merge(dia, venta.getTotal() == null ? 0 : venta.getTotal().doubleValue(), Double::sum);
        }
        List<Map<String, Object>> evolucion = new ArrayList<>();
        double totalPeriodo = 0;
        double pico = 0;
        for (Map.Entry<String, Double> e : porDia.entrySet()) {
            totalPeriodo += e.getValue();
            pico = Math.max(pico, e.getValue());
            evolucion.add(Map.of("fecha", e.getKey(), "total", e.getValue()));
        }

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("empresa", Map.of("id_empresa", empresa.getIdEmpresa(), "nombre", empresa.getNombre()));
        Map<String, Object> filtro = new LinkedHashMap<>();
        filtro.put("id_sucursal", idSucursal);
        filtro.put("sucursales_disponibles", sucursales.stream()
                .map(s -> Map.<String, Object>of("id_sucursal", s.getIdSucursal(), "nombre", s.getNombre()))
                .toList());
        dashboard.put("filtro", filtro);
        Map<String, Object> indicadores = new LinkedHashMap<>();
        indicadores.put("ventas_hoy", montoHoy);
        indicadores.put("ticket_promedio", cantidadHoy == 0 ? 0 : Math.round((montoHoy / cantidadHoy) * 100.0) / 100.0);
        indicadores.put("cajas_abiertas", cajasAbiertas);
        indicadores.put("producto_estrella", productoEstrella);
        indicadores.put("productos_bajo_stock", bajo);
        indicadores.put("productos_agotados", agotados);
        indicadores.put("ingresos_dia", montoHoy);
        indicadores.put("egresos_dia", 0);
        indicadores.put("flujo_neto", montoHoy);
        dashboard.put("indicadores", indicadores);
        dashboard.put("evolucion_ventas_30_dias", Map.of(
                "total_periodo", Math.round(totalPeriodo * 100.0) / 100.0,
                "pico_diario", Math.round(pico * 100.0) / 100.0,
                "puntos", evolucion));
        dashboard.put("ventas_por_sucursal", List.of());
        dashboard.put("ranking_clientes", List.of());
        dashboard.put("recomendaciones_ia", List.of());
        dashboard.put("generado_en", LocalDateTime.now().toString());
        return dashboard;
    }

    private List<Venta> ventas(Integer idEmpresa, Integer idSucursal, LocalDateTime desde, LocalDateTime hasta) {
        if (idSucursal == null) {
            return entityManager.createQuery("""
                    select v from Venta v, CajaSesion cs
                    join cs.caja c
                    join c.sucursal s
                    where v.idCajaSesion = cs.idCajaSesion
                      and s.empresa.idEmpresa = :idEmpresa
                      and v.fecha between :desde and :hasta
                      and v.estado <> 'ANULADA'
                    """, Venta.class)
                    .setParameter("idEmpresa", idEmpresa)
                    .setParameter("desde", desde)
                    .setParameter("hasta", hasta)
                    .getResultList();
        }
        return entityManager.createQuery("""
                select v from Venta v, CajaSesion cs
                join cs.caja c
                join c.sucursal s
                where v.idCajaSesion = cs.idCajaSesion
                  and s.empresa.idEmpresa = :idEmpresa
                  and s.idSucursal = :idSucursal
                  and v.fecha between :desde and :hasta
                  and v.estado <> 'ANULADA'
                """, Venta.class)
                .setParameter("idEmpresa", idEmpresa)
                .setParameter("idSucursal", idSucursal)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta)
                .getResultList();
    }
}
