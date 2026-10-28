package com.pos.reportes.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.pos.common.access.EmpresaAccess;
import com.pos.common.ai.GeminiClient;
import com.pos.common.exception.ApiException;
import com.pos.empresas.domain.Empresa;
import com.pos.empresas.domain.Sucursal;
import com.pos.empresas.repo.EmpresaRepository;
import com.pos.empresas.repo.SucursalRepository;
import com.pos.empresas.service.SignoMovimientoCaja;
import com.pos.inventario.domain.MovimientoInventario;
import com.pos.inventario.domain.Stock;
import com.pos.productos.domain.Producto;
import com.pos.productos.repo.ProductoRepository;
import com.pos.reportes.dto.ReporteDtos;
import com.pos.usuarios.domain.Usuario;
import com.pos.ventas.domain.Venta;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

/**
 * Reportes estaticos, parametrizados y dinamicos. Todas las formas de generar un reporte comparten las
 * mismas consultas y reglas (ventas validas, estado de stock, signo de caja), de modo que un mismo dato
 * da el mismo resultado sin importar desde donde se pida.
 */
@Service
public class ReportesService {

    /** Permiso que exige cualquier reporte (el mismo que usa el menu del frontend). */
    private static final String PERMISO_REPORTES = "REPORTE_GENERAR";

    /** Ventas que no cuentan en ningun reporte; la analitica y el QBE usan la misma lista. */
    public static final String VENTA_VALIDA_JPQL = "upper(v.estado) not in ('ANULADA', 'CANCELADA')";

    private static final String METODO_CREDITO = "Crédito (pendiente de cobro)";
    private static final int LOTE_IN = 1000;
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Ventas de la empresa en el rango; usar con alias v (venta), cs, c (caja) y s (sucursal). */
    private static final String FROM_VENTAS = """
             from Venta v, CajaSesion cs
             join cs.caja c
             join c.sucursal s
            where v.idCajaSesion = cs.idCajaSesion
              and s.empresa.idEmpresa = :idEmpresa
              and v.fecha between :desde and :hasta
              and """ + " " + VENTA_VALIDA_JPQL + "\n";

    /** Lineas de venta de la empresa en el rango, con producto y categoria (alias d, v, s, p, cat). */
    private static final String FROM_LINEAS = """
             from DetalleVenta d
             join d.venta v,
                  CajaSesion cs
             join cs.caja c
             join c.sucursal s,
                  Producto p
             left join p.subcategoria sub
             left join sub.categoriaProducto cat
            where v.idCajaSesion = cs.idCajaSesion
              and d.idProducto = p.idProducto
              and s.empresa.idEmpresa = :idEmpresa
              and v.fecha between :desde and :hasta
              and """ + " " + VENTA_VALIDA_JPQL + "\n";

    private final EmpresaAccess empresaAccess;
    private final com.pos.common.access.DataReadPolicy dataPolicy;
    private final EmpresaRepository empresaRepository;
    private final SucursalRepository sucursalRepository;
    private final ProductoRepository productoRepository;
    private final GeminiClient geminiClient;
    private final TransactionTemplate lectura;

    @PersistenceContext
    private EntityManager entityManager;

    public ReportesService(
            EmpresaAccess empresaAccess, com.pos.common.access.DataReadPolicy dataPolicy,
            EmpresaRepository empresaRepository,
            SucursalRepository sucursalRepository,
            ProductoRepository productoRepository,
            GeminiClient geminiClient,
            PlatformTransactionManager transactionManager) {
        this.empresaAccess = empresaAccess; this.dataPolicy=dataPolicy;
        this.empresaRepository = empresaRepository;
        this.sucursalRepository = sucursalRepository;
        this.productoRepository = productoRepository;
        this.geminiClient = geminiClient;
        this.lectura = new TransactionTemplate(transactionManager);
        this.lectura.setReadOnly(true);
    }

    public List<ReporteDtos.Plantilla> catalogo() {
        return List.of(
                plantilla("movimientos_inventario", "Movimientos de inventario",
                        "Historial de movimientos de stock por producto, tipo y sucursal.",
                        List.of("cantidad"), List.of("fecha", "sucursal", "producto", "tipo_movimiento", "direccion"),
                        "tabla", Map.of("periodo", "ultimos_30_dias"),
                        List.of("inventario", "movimientos", "historial", "kardex")),
                plantilla("resumen_ventas", "Resumen de ventas",
                        "Resumen ejecutivo de ventas por periodo y sucursal.",
                        List.of("total_ventas", "cantidad_ventas", "ticket_promedio"),
                        List.of("sucursal"), "tabla",
                        Map.of("periodo", "ultimos_30_dias"),
                        List.of("ventas", "resumen", "sucursal", "periodo")),
                plantilla("ventas_por_sucursal", "Ventas por sucursal",
                        "Totales de ventas agrupados por sucursal.",
                        List.of("total_ventas", "cantidad_ventas"), List.of("sucursal"), "barra",
                        Map.of("periodo", "ultimos_30_dias"), List.of("ventas", "sucursal", "comparativo")),
                plantilla("ventas_por_producto", "Ventas por producto",
                        "Ventas, cantidades y categoria de producto dentro de un periodo.",
                        List.of("total_ventas", "cantidad_vendida", "precio_promedio"),
                        List.of("producto", "categoria_producto"), "tabla",
                        Map.of("periodo", "ultimos_30_dias", "top_n", 10),
                        List.of("ventas", "producto", "categoria", "top")),
                plantilla("productos_top", "Productos mas vendidos",
                        "Top N de productos por cantidad vendida.",
                        List.of("cantidad_vendida", "total_ventas"), List.of("producto"), "tabla",
                        Map.of("periodo", "ultimos_30_dias", "top_n", 10),
                        List.of("top", "producto", "ventas", "cantidad")),
                plantilla("alertas_stock", "Alertas de stock",
                        "Productos agotados o con stock igual o inferior al minimo (estado actual).",
                        List.of("stock_actual", "stock_minimo", "faltante"),
                        List.of("sucursal", "producto", "estado"), "tabla",
                        Map.of("solo_bajo_minimo", true), List.of("stock", "inventario", "alertas")),
                plantilla("movimientos_caja", "Movimientos de caja",
                        "Ingresos, egresos y neto de caja por periodo y sucursal.",
                        List.of("ingresos", "egresos", "neto"),
                        List.of("fecha", "sucursal", "caja", "tipo_movimiento"), "tabla",
                        Map.of("periodo", "ultimos_30_dias"), List.of("caja", "movimientos", "saldo")),
                plantilla("reparticion_metodos_pago", "Reparticion por metodo de pago",
                        "Distribucion de ventas por metodo de pago (las ventas a credito aparecen aparte).",
                        List.of("total_ventas", "cantidad_transacciones"), List.of("metodo_pago"), "torta",
                        Map.of("periodo", "ultimos_30_dias"), List.of("pago", "ventas", "metodo")),
                plantilla("comparar_periodos", "Comparar periodos",
                        "Comparacion entre un periodo actual y el periodo anterior de igual duracion.",
                        List.of("total_ventas", "cantidad_ventas", "crecimiento_pct"), List.of("periodo"), "tarjetas",
                        Map.of("periodo", "ultimos_30_dias"), List.of("comparativo", "periodo", "crecimiento")),
                plantilla("ventas_detalle", "Ventas (detalle)",
                        "Listado de ventas (una fila por venta) con detalles basicos.",
                        List.of(), List.of("id_venta", "fecha", "sucursal", "cliente", "metodo_pago", "total"),
                        "tabla", Map.of("periodo", "ultimos_30_dias"), List.of("ventas", "detalle", "historial")));
    }

    // ---------------------------------------------------------------------------------------------
    // Reportes estaticos
    // ---------------------------------------------------------------------------------------------

    /** Resumen de ventas de un dia. Incluido en el plan gratuito (reportes diarios), por eso no exige plan. */
    @Transactional(readOnly = true)
    public Map<String, Object> resumenVentas(Usuario usuario, Integer empresaId, LocalDate fecha) {
        empresaAccess.requireEmpresa(usuario, empresaId);
        LocalDate dia = fecha == null ? LocalDate.now() : fecha;
        Map<Integer, Object[]> totales = totalesPorSucursal(empresaId, dia, dia);

        Map<Integer, Integer> unidadesPorSucursal = new HashMap<>();
        Map<Integer, List<Map<String, Object>>> topPorSucursal = new HashMap<>();
        List<Object[]> lineas = consulta("select s.idSucursal, p.idProducto, p.nombre, sum(d.cantidad)" + FROM_LINEAS
                + " group by s.idSucursal, p.idProducto, p.nombre order by sum(d.cantidad) desc, p.nombre",
                Object[].class, paramsRango(empresaId, dia, dia)).getResultList();
        for (Object[] row : lineas) {
            Integer idSucursal = aInt(row[0]);
            int unidades = aInt(row[3]);
            unidadesPorSucursal.merge(idSucursal, unidades, Integer::sum);
            List<Map<String, Object>> top = topPorSucursal.computeIfAbsent(idSucursal, k -> new ArrayList<>());
            if (top.size() < 5) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("posicion", top.size() + 1);
                item.put("id_producto", row[1]);
                item.put("producto", row[2]);
                item.put("unidades", unidades);
                top.add(item);
            }
        }

        List<Map<String, Object>> sucursalDtos = new ArrayList<>();
        int totalVentas = 0;
        double montoVendido = 0;
        int productosVendidos = 0;
        for (Sucursal sucursal : sucursales) {
            List<Venta> ventas = ventasDeSucursal(sucursal.getIdSucursal(), desde, hasta);
            int cantidad = ventas.size();
            double monto = ventas.stream().map(Venta::getTotal).mapToDouble(v -> v == null ? 0 : v.doubleValue()).sum();
            int unidades = 0;
            Map<Integer, Integer> top = new LinkedHashMap<>();
            Map<Integer, String> nombres = new LinkedHashMap<>();
            for (Venta venta : ventas) {
                for (DetalleVenta detalle : detalles(venta.getIdVenta())) {
                    unidades += detalle.getCantidad() == null ? 0 : detalle.getCantidad();
                    top.merge(detalle.getIdProducto(), detalle.getCantidad(), Integer::sum);
                    productoRepository.findById(detalle.getIdProducto())
                            .ifPresent(p -> nombres.put(p.getIdProducto(), p.getNombre()));
                }
            }
            List<Map<String, Object>> topProductos = top.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                    .limit(5)
                    .map(e -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("posicion", 0);
                        row.put("id_producto", e.getKey());
                        row.put("producto", nombres.getOrDefault(e.getKey(), "Producto " + e.getKey()));
                        row.put("unidades", e.getValue());
                        return row;
                    })
                    .toList();
            for (int i = 0; i < topProductos.size(); i++) {
                topProductos.get(i).put("posicion", i + 1);
            }
            Map<String, Object> ventasDia = new LinkedHashMap<>();
            ventasDia.put("total_ventas", cantidad);
            ventasDia.put("monto_vendido", round(monto));
            ventasDia.put("ticket_promedio", cantidad == 0 ? 0 : round(monto / cantidad));
            ventasDia.put("productos_vendidos", unidades);
            Map<String, Object> sucursalDto = new LinkedHashMap<>();
            sucursalDto.put("id_sucursal", sucursal.getIdSucursal());
            sucursalDto.put("sucursal", sucursal.getNombre());
            sucursalDto.put("ventas_dia", resumenMontos(cantidad, monto, unidades));
            sucursalDto.put("top_productos", topPorSucursal.getOrDefault(sucursal.getIdSucursal(), List.of()));
            sucursalDtos.add(sucursalDto);
            totalVentas += cantidad;
            montoVendido += monto;
            productosVendidos += unidades;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id_empresa", empresaId);
        response.put("fecha", dia.toString());
        response.put("sucursales", sucursalDtos);
        response.put("total_empresa", resumenMontos(totalVentas, montoVendido, productosVendidos));
        return response;
    }

    /** Detalle de ventas de un dia. Incluido en el plan gratuito (reportes diarios), por eso no exige plan. */
    @Transactional(readOnly = true)
    public Map<String, Object> detalleVentas(Usuario usuario, Integer empresaId, LocalDate fecha) {
        empresaAccess.requireEmpresa(usuario, empresaId);
        LocalDate dia = fecha == null ? LocalDate.now() : fecha;
        Map<Integer, List<Map<String, Object>>> porSucursal = new HashMap<>();
        for (Object[] row : ventasConSucursal(empresaId, dia, dia)) {
            Venta venta = (Venta) row[0];
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id_venta", venta.getIdVenta());
            fila.put("numero_venta", String.valueOf(venta.getIdVenta()));
            fila.put("hora", venta.getFecha() == null ? "" : venta.getFecha().format(HORA));
            fila.put("cliente", venta.getIdCliente() == null ? null : "Cliente " + venta.getIdCliente());
            fila.put("subtotal", round(aDouble(venta.getSubtotal())));
            fila.put("descuento", round(aDouble(venta.getDescuentoTotal())));
            fila.put("total", round(aDouble(venta.getTotal())));
            porSucursal.computeIfAbsent(aInt(row[1]), k -> new ArrayList<>()).add(fila);
        }
        List<Map<String, Object>> sucursalDtos = new ArrayList<>();
        int totalRegistros = 0;
        double totalVendido = 0;
        for (Sucursal sucursal : sucursales(empresaId)) {
            List<Map<String, Object>> filas = porSucursal.getOrDefault(sucursal.getIdSucursal(), List.of());
            double monto = sumarColumna(filas, "total");
            Map<String, Object> sucursalDto = new LinkedHashMap<>();
            sucursalDto.put("id_sucursal", sucursal.getIdSucursal());
            sucursalDto.put("sucursal", sucursal.getNombre());
            sucursalDto.put("ventas", filas);
            sucursalDto.put("resumen_sucursal", mapa("total_registros", filas.size(), "total_vendido", round(monto)));
            sucursalDtos.add(sucursalDto);
            totalRegistros += filas.size();
            totalVendido += monto;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id_empresa", empresaId);
        response.put("fecha", dia.toString());
        response.put("sucursales", sucursalDtos);
        response.put("total_empresa", mapa("total_registros", totalRegistros, "total_vendido", round(totalVendido)));
        return response;
    }

    /** Estado actual del stock por sucursal. */
    @Transactional(readOnly = true)
    public Map<String, Object> estadoInventario(Usuario usuario, Integer empresaId) {
        empresaAccess.requirePlanFeature(usuario, empresaId, "reportes_estandar");
        empresaAccess.requireEmpresa(usuario, empresaId);
        List<Map<String, Object>> sucursalDtos = new ArrayList<>();
        Map<String, Object> total = resumenStock(List.of());
        for (Sucursal sucursal : sucursales(empresaId)) {
            List<Map<String, Object>> productos = porSucursal.getOrDefault(sucursal.getIdSucursal(), List.of());
            Map<String, Object> resumen = resumenStock(productos);
            Map<String, Object> sucursalDto = new LinkedHashMap<>();
            sucursalDto.put("id_sucursal", sucursal.getIdSucursal());
            sucursalDto.put("sucursal", sucursal.getNombre());
            sucursalDto.put("productos", productos);
            sucursalDto.put("resumen_sucursal", resumen);
            sucursalDtos.add(sucursalDto);
            resumen.forEach((k, v) -> total.merge(k, v, (a, b) -> aInt(a) + aInt(b)));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id_empresa", empresaId);
        response.put("fecha", LocalDate.now().toString());
        response.put("sucursales", sucursalDtos);
        response.put("total_empresa", total);
        return response;
    }

    /** Movimientos de inventario del rango (por defecto, los ultimos 7 dias). */
    @Transactional(readOnly = true)
    public Map<String, Object> movimientosInventario(Usuario usuario, Integer empresaId) {
        empresaAccess.requirePlanFeature(usuario, empresaId, "reportes_estandar");
        empresaAccess.requireEmpresa(usuario, empresaId);
        LocalDate fin = LocalDate.now();
        LocalDate inicio = fin.minusDays(29);
        List<Map<String, Object>> sucursalDtos = new ArrayList<>();
        int totalEntradas = 0;
        int totalSalidas = 0;
        for (Sucursal sucursal : sucursales(empresaId)) {
            List<Map<String, Object>> movimientos = porSucursal.getOrDefault(sucursal.getIdSucursal(), List.of());
            int[] es = entradasSalidas(movimientos);
            Map<String, Object> sucursalDto = new LinkedHashMap<>();
            sucursalDto.put("id_sucursal", sucursal.getIdSucursal());
            sucursalDto.put("sucursal", sucursal.getNombre());
            sucursalDto.put("movimientos", movimientos);
            sucursalDto.put("resumen_sucursal", mapa("total_entradas", es[0], "total_salidas", es[1]));
            sucursalDtos.add(sucursalDto);
            totalEntradas += es[0];
            totalSalidas += es[1];
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id_empresa", empresaId);
        response.put("fecha_inicio", inicio.toString());
        response.put("fecha_fin", fin.toString());
        response.put("sucursales", sucursalDtos);
        response.put("total_empresa", mapa("total_entradas", totalEntradas, "total_salidas", totalSalidas));
        return response;
    }

    /** Cajas que estuvieron abiertas durante el dia y el flujo de efectivo de ese dia. */
    @Transactional(readOnly = true)
    public Map<String, Object> resumenCajas(Usuario usuario, Integer empresaId) {
        empresaAccess.requirePlanFeature(usuario, empresaId, "reportes_estandar");
        empresaAccess.requireEmpresa(usuario, empresaId);
        List<?> rows = entityManager.createQuery("""
                select s.idSucursal, s.nombre, c.idCaja, c.nombre, cs.idCajaSesion, cs.estado, cs.fechaApertura, cs.fechaCierre
                from CajaSesion cs
                join cs.caja c
                join c.sucursal s
                where s.empresa.idEmpresa = :idEmpresa
                order by s.nombre, c.nombre
                """)
                .setParameter("idEmpresa", empresaId)
                .getResultList();
        Map<Integer, Map<String, Object>> porSucursal = new LinkedHashMap<>();
        int totalCajas = 0;
        int abiertas = 0;
        int cerradas = 0;
        for (Object raw : rows) {
            Object[] row = (Object[]) raw;
            Integer idSucursal = (Integer) row[0];
            Map<String, Object> sucursalDto = porSucursal.computeIfAbsent(idSucursal, id -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id_sucursal", id);
                map.put("sucursal", row[1]);
                map.put("cajas", new ArrayList<Map<String, Object>>());
                return map;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cajas = (List<Map<String, Object>>) sucursalDto.get("cajas");
            Map<String, Object> caja = new LinkedHashMap<>();
            caja.put("id_caja", row[2]);
            caja.put("id_caja_sesion", row[4]);
            caja.put("caja", row[3]);
            caja.put("estado", row[5]);
            caja.put("apertura", row[6] == null ? "" : row[6].toString());
            caja.put("cierre", row[7] == null ? null : row[7].toString());
            cajas.add(caja);
            totalCajas++;
            if ("Abierto".equalsIgnoreCase(String.valueOf(row[5]))) {
                abiertas++;
            } else {
                cerradas++;
            }
        }
        List<Map<String, Object>> sucursales = new ArrayList<>();
        for (Map<String, Object> sucursalDto : porSucursal.values()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cajas = (List<Map<String, Object>>) sucursalDto.get("cajas");
            long abiertasLocal = cajas.stream().filter(c -> "Abierto".equalsIgnoreCase(String.valueOf(c.get("estado")))).count();
            sucursalDto.put("resumen_sucursal", Map.of(
                    "total_cajas", cajas.size(),
                    "cajas_abiertas", abiertasLocal,
                    "cajas_cerradas", cajas.size() - abiertasLocal,
                    "ingresos", 0,
                    "egresos", 0,
                    "flujo_neto", 0));
            sucursales.add(sucursalDto);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id_empresa", empresaId);
        response.put("fecha", dia.toString());
        response.put("sucursales", sucursalDtos);
        response.put("total_empresa", total);
        return response;
    }

    /** Movimientos de caja de un dia. */
    @Transactional(readOnly = true)
    public Map<String, Object> movimientosCaja(Usuario usuario, Integer empresaId) {
        empresaAccess.requirePlanFeature(usuario, empresaId, "reportes_estandar");
        empresaAccess.requireEmpresa(usuario, empresaId);
        List<?> rows = entityManager.createQuery("""
                select s.idSucursal, s.nombre, mc.idMovimientoCaja, mc.fecha, c.nombre, t.nombre, mc.concepto, mc.monto, c.idCaja, t.idTipoMovimientoCaja, cs.estado
                from MovimientoCaja mc
                join mc.cajaSesion cs
                join cs.caja c
                join c.sucursal s
                join mc.tipoMovimientoCaja t
                where s.empresa.idEmpresa = :idEmpresa
                order by mc.fecha desc
                """)
                .setParameter("idEmpresa", empresaId)
                .getResultList();
        Map<Integer, Map<String, Object>> porSucursal = new LinkedHashMap<>();
        int totalMovs = 0;
        double ingresos = 0;
        double egresos = 0;
        for (Sucursal sucursal : sucursales(empresaId)) {
            List<Map<String, Object>> movimientos = porSucursal.getOrDefault(sucursal.getIdSucursal(), List.of());
            double[] ie = ingresosEgresos(movimientos);
            Map<String, Object> sucursalDto = new LinkedHashMap<>();
            sucursalDto.put("id_sucursal", sucursal.getIdSucursal());
            sucursalDto.put("sucursal", sucursal.getNombre());
            sucursalDto.put("movimientos", movimientos);
            sucursalDto.put("resumen_sucursal", resumenMovimientosCaja(movimientos.size(), ie));
            sucursalDtos.add(sucursalDto);
            totalMovs += movimientos.size();
            ingresos += ie[0];
            egresos += ie[1];
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id_empresa", empresaId);
        response.put("fecha", dia.toString());
        response.put("sucursales", sucursalDtos);
        response.put("total_empresa", resumenMovimientosCaja(totalMovs, new double[] { ingresos, egresos }));
        return response;
    }

    public ReporteDtos.RespuestaInterpretacion interpretar(Integer empresaId, String prompt) {
        empresaAccess.requirePlanFeature(com.pos.common.security.SecurityUtils.currentUser(), empresaId, "reportes_avanzados");
        empresaRepository.findById(empresaId).orElseThrow(() -> ApiException.notFound("La empresa no existe."));
        ReporteDtos.RespuestaInterpretacion conIa = interpretarConIa(prompt);
        if (conIa != null) {
            return conIa;
        }
        String texto = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        String id = "resumen_ventas";
        if (texto.contains("stock") || texto.contains("alerta")) {
            id = "alertas_stock";
        } else if (texto.contains("kardex") || texto.contains("inventario") && texto.contains("mov")) {
            id = "movimientos_inventario";
        } else if (texto.contains("caja")) {
            id = "movimientos_caja";
        } else if (texto.contains("metodo") || texto.contains("método") || texto.contains("pago")) {
            id = "reparticion_metodos_pago";
        } else if (texto.contains("compar")) {
            id = "comparar_periodos";
        } else if (texto.contains("top") || texto.contains("mas vend") || texto.contains("más vend")) {
            id = "productos_top";
        } else if (texto.contains("producto")) {
            id = "ventas_por_producto";
        } else if (texto.contains("sucursal")) {
            id = "ventas_por_sucursal";
        } else if (texto.contains("detalle")) {
            id = "ventas_detalle";
        }
        final String identificador = id;
        List<ReporteDtos.Plantilla> plantillas = catalogo();
        ReporteDtos.Plantilla plantilla = plantillas.stream()
                .filter(p -> p.getIdentificador().equals(identificador))
                .findFirst()
                .orElse(plantillas.get(0));
        Map<String, Object> filtros = new LinkedHashMap<>(plantilla.getFiltrosPorDefecto());
        String periodo = periodoDesdeTexto(texto);
        if (periodo != null) {
            filtros.put("periodo", periodo);
        }
        ReporteDtos.Especificacion spec = new ReporteDtos.Especificacion();
        spec.setIdentificadorPlantilla(plantilla.getIdentificador());
        spec.setTitulo(plantilla.getNombre());
        spec.setMetricas(plantilla.getMetricas());
        spec.setDimensiones(plantilla.getDimensiones());
        spec.setFiltros(filtros);
        spec.setFormato(plantilla.getFormato());
        spec.setConfianza(0.7);
        ReporteDtos.RespuestaInterpretacion respuesta = new ReporteDtos.RespuestaInterpretacion();
        respuesta.setEspecificacion(spec);
        respuesta.setPlantilla(plantilla);
        respuesta.setAdvertencias(List.of(geminiClient.isEnabled()
                ? "La IA no respondio; se uso la interpretacion local por palabras clave."
                : "Interpretacion local por palabras clave (configura GEMINI_API_KEY para usar IA)."));
        return respuesta;
    }

    private static String periodoDesdeTexto(String texto) {
        if (texto.contains("hoy")) {
            return "hoy";
        }
        if (texto.contains("semana") || texto.contains("7 dias") || texto.contains("7 días")) {
            return "ultimos_7_dias";
        }
        if (texto.contains("90 dias") || texto.contains("90 días") || texto.contains("trimestre")) {
            return "ultimos_90_dias";
        }
        if (texto.contains("este mes")) {
            return "este_mes";
        }
        if (texto.contains("este año") || texto.contains("este anio")) {
            return "este_anio";
        }
        return null;
    }

    private static final String INSTRUCCIONES_IA = """
            Eres el asistente de reportes de un sistema de punto de venta (POS).
            Recibes un pedido en lenguaje natural y debes elegir UNA plantilla del catalogo.
            Responde solo con JSON con esta forma:
            {"identificador": "<id de la plantilla>", "titulo": "<titulo breve en español>",
             "periodo": "hoy|ultimos_7_dias|ultimos_30_dias|ultimos_90_dias|este_mes|este_anio",
             "top_n": <numero o null>, "solicita_aclaracion": <true|false>,
             "pregunta": "<pregunta si falta informacion, o null>", "confianza": <0 a 1>}
            Si el pedido no tiene relacion con ventas, inventario, caja o pagos, usa solicita_aclaracion=true.
            """;

    /** Usa Google Gemini para elegir la plantilla. Devuelve null si la IA no esta configurada o falla. */
    private ReporteDtos.RespuestaInterpretacion interpretarConIa(String prompt) {
        if (!geminiClient.isEnabled() || prompt == null || prompt.isBlank()) {
            return null;
        }
        List<ReporteDtos.Plantilla> plantillas = catalogo();
        StringBuilder mensaje = new StringBuilder("Catalogo de plantillas:\n");
        for (ReporteDtos.Plantilla p : plantillas) {
            mensaje.append("- ").append(p.getIdentificador()).append(": ").append(p.getNombre())
                    .append(". ").append(p.getDescripcion()).append('\n');
        }
        mensaje.append("\nPedido del usuario: ").append(prompt.trim());

        return geminiClient.generarJson(INSTRUCCIONES_IA, mensaje.toString())
                .map(json -> {
                    String id = json.path("identificador").asText("");
                    boolean pideAclaracion = json.path("solicita_aclaracion").asBoolean(false);
                    ReporteDtos.Plantilla plantilla = plantillas.stream()
                            .filter(p -> p.getIdentificador().equals(id))
                            .findFirst()
                            .orElse(pideAclaracion ? plantillas.get(0) : null);
                    if (plantilla == null) {
                        return null;
                    }
                    Map<String, Object> filtros = new LinkedHashMap<>(plantilla.getFiltrosPorDefecto());
                    String periodo = json.path("periodo").asText("");
                    if (!periodo.isBlank() && !"null".equals(periodo)) {
                        filtros.put("periodo", periodo);
                    }
                    if (json.path("top_n").isNumber()) {
                        filtros.put("top_n", json.path("top_n").asInt());
                    }
                    ReporteDtos.Especificacion spec = new ReporteDtos.Especificacion();
                    spec.setIdentificadorPlantilla(plantilla.getIdentificador());
                    String titulo = json.path("titulo").asText("");
                    spec.setTitulo(titulo.isBlank() ? plantilla.getNombre() : titulo);
                    spec.setMetricas(plantilla.getMetricas());
                    spec.setDimensiones(plantilla.getDimensiones());
                    spec.setFiltros(filtros);
                    spec.setFormato(plantilla.getFormato());
                    spec.setSolicitaAclaracion(pideAclaracion);
                    String pregunta = json.path("pregunta").asText("");
                    spec.setPregunta(pregunta.isBlank() || "null".equals(pregunta) ? null : pregunta);
                    spec.setConfianza(json.path("confianza").isNumber() ? json.path("confianza").asDouble() : 0.9);
                    ReporteDtos.RespuestaInterpretacion respuesta = new ReporteDtos.RespuestaInterpretacion();
                    respuesta.setEspecificacion(spec);
                    respuesta.setPlantilla(plantilla);
                    respuesta.setAdvertencias(List.of("Interpretado con IA (Google Gemini, modelo " + geminiClient.getModel() + ")."));
                    return respuesta;
                })
                .orElse(null);
    }

    /**
     * Interpreta el pedido (posible llamada a la IA) fuera de la transaccion y luego consulta los datos en
     * una transaccion de solo lectura, para no retener una conexion a la base mientras responde la IA.
     */
    public ReporteDtos.RespuestaReporte ejecutar(Usuario usuario, Integer empresaId, String prompt) {
        empresaAccess.requirePlanFeature(usuario, empresaId, "reportes_avanzados");
        empresaAccess.requireEmpresa(usuario, empresaId);
        ReporteDtos.RespuestaInterpretacion interpretacion = interpretar(empresaId, prompt);
        String id = interpretacion.getEspecificacion().getIdentificadorPlantilla();
        if (id == null || id.isBlank()) {
            id = "resumen_ventas";
        }
        String source=id.contains("inventario")?"inventory_movement:read":id.equals("alertas_stock")?"inventory:read":id.equals("movimientos_caja")?"cash_movement:read":"sale:read";
        requireReport(usuario,empresaId,source);
        if(id.contains("producto")) dataPolicy.productFields(dataPolicy.require(usuario,empresaId,null,"product:read"),"nombre","id_subcategoria");
        if(id.equals("ventas_detalle")) dataPolicy.require(usuario,empresaId,null,"client:read");
        Map<String, Object> filtros = interpretacion.getEspecificacion().getFiltros();
        LocalDate[] rango = rangoPeriodo(filtros);
        LocalDate desde = rango[0];
        LocalDate hasta = rango[1];
        List<Map<String, Object>> filas = new ArrayList<>();
        Map<String, Object> agregados = new LinkedHashMap<>();
        List<ReporteDtos.Columna> columnas = new ArrayList<>();
        if (Boolean.TRUE.equals(interpretacion.getEspecificacion().getSolicitaAclaracion())) {
            // La IA necesita mas informacion: no se ejecuta ningun reporte, se devuelve la pregunta.
        } else if ("alertas_stock".equals(id)) {
            for (Map<String, Object> stock : filasStock(empresaId, true)) {
                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("sucursal", stock.get("sucursal"));
                fila.put("producto", stock.get("producto"));
                fila.put("stock_actual", stock.get("stock_actual"));
                fila.put("stock_minimo", stock.get("stock_minimo"));
                fila.put("faltante", Math.max(0, aInt(stock.get("stock_minimo")) - aInt(stock.get("stock_actual"))));
                fila.put("estado", stock.get("estado"));
                filas.add(fila);
            }
            columnas.add(columna("sucursal", "Sucursal", "texto"));
            columnas.add(columna("producto", "Producto", "texto"));
            columnas.add(columna("stock_actual", "Stock actual", "entero"));
            columnas.add(columna("stock_minimo", "Stock minimo", "entero"));
            columnas.add(columna("faltante", "Faltante", "entero"));
            columnas.add(columna("estado", "Estado", "texto"));
            agregados.put("productos_en_alerta", filas.size());
            agregados.put("productos_agotados", filas.stream().filter(f -> "agotado".equals(f.get("estado"))).count());
            agregados.put("fecha", LocalDate.now().toString());
        } else if ("movimientos_inventario".equals(id)) {
            for (Map<String, Object> mov : filasMovimientosInventario(empresaId, desde, hasta, null, null, null, null)) {
                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("fecha", mov.get("fecha_hora"));
                fila.put("sucursal", mov.get("sucursal"));
                fila.put("producto", mov.get("producto"));
                fila.put("tipo_movimiento", mov.get("tipo_movimiento"));
                fila.put("direccion", mov.get("direccion"));
                fila.put("cantidad", mov.get("cantidad"));
                filas.add(fila);
            }
            columnas.add(columna("fecha", "Fecha", "texto"));
            columnas.add(columna("sucursal", "Sucursal", "texto"));
            columnas.add(columna("producto", "Producto", "texto"));
            columnas.add(columna("tipo_movimiento", "Tipo de movimiento", "texto"));
            columnas.add(columna("direccion", "Direccion", "texto"));
            columnas.add(columna("cantidad", "Cantidad", "entero"));
            int[] es = entradasSalidas(filas);
            agregados.put("total_entradas", es[0]);
            agregados.put("total_salidas", es[1]);
            agregarRango(agregados, desde, hasta);
        } else if ("movimientos_caja".equals(id)) {
            List<Map<String, Object>> movimientos = filasMovimientosCaja(empresaId, desde, hasta, null, null, null, null);
            for (Map<String, Object> mov : movimientos) {
                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("fecha", mov.get("fecha_hora"));
                fila.put("sucursal", mov.get("sucursal"));
                fila.put("caja", mov.get("caja"));
                fila.put("tipo_movimiento", mov.get("tipo"));
                fila.put("concepto", mov.get("concepto"));
                fila.put("monto", mov.get("monto"));
                filas.add(fila);
            }
            columnas.add(columna("fecha", "Fecha", "texto"));
            columnas.add(columna("sucursal", "Sucursal", "texto"));
            columnas.add(columna("caja", "Caja", "texto"));
            columnas.add(columna("tipo_movimiento", "Tipo", "texto"));
            columnas.add(columna("concepto", "Concepto", "texto"));
            columnas.add(columna("monto", "Monto", "moneda"));
            double[] ie = ingresosEgresos(movimientos);
            agregados.put("ingresos", round(ie[0]));
            agregados.put("egresos", round(ie[1]));
            agregados.put("neto", round(ie[0] - ie[1]));
            agregarRango(agregados, desde, hasta);
        } else if ("ventas_detalle".equals(id)) {
            filas.addAll(filasDetalleVentas(empresaId, desde, hasta));
            columnas.add(columna("id_venta", "Venta", "entero"));
            columnas.add(columna("fecha", "Fecha", "texto"));
            columnas.add(columna("sucursal", "Sucursal", "texto"));
            columnas.add(columna("cliente", "Cliente", "texto"));
            columnas.add(columna("metodo_pago", "Metodo de pago", "texto"));
            columnas.add(columna("total", "Total", "moneda"));
            agregados.put("registros", filas.size());
            agregados.put("total_ventas", round(sumarColumna(filas, "total")));
            agregarRango(agregados, desde, hasta);
        } else if ("ventas_por_sucursal".equals(id)) {
            filas.addAll(filasVentasPorSucursal(empresaId, desde, hasta));
            columnas.add(columna("sucursal", "Sucursal", "texto"));
            columnas.add(columna("total_ventas", "Total de ventas", "moneda"));
            columnas.add(columna("cantidad_ventas", "Cantidad de ventas", "entero"));
            agregados.put("cantidad_ventas", aInt(sumarColumna(filas, "cantidad_ventas")));
            agregados.put("total_ventas", round(sumarColumna(filas, "total_ventas")));
            agregarRango(agregados, desde, hasta);
        } else if ("productos_top".equals(id)) {
            filas.addAll(filasPorProducto(empresaId, desde, hasta, topN(filtros), false));
            columnas.add(columna("producto", "Producto", "texto"));
            columnas.add(columna("cantidad_vendida", "Cantidad vendida", "entero"));
            columnas.add(columna("total_ventas", "Total", "moneda"));
            agregados.put("cantidad_vendida", aInt(sumarColumna(filas, "cantidad_vendida")));
            agregarRango(agregados, desde, hasta);
        } else if ("ventas_por_producto".equals(id)) {
            filas.addAll(filasPorProducto(empresaId, desde, hasta, topN(filtros), true));
            columnas.add(columna("producto", "Producto", "texto"));
            columnas.add(columna("categoria_producto", "Categoria", "texto"));
            columnas.add(columna("cantidad_vendida", "Cantidad vendida", "entero"));
            columnas.add(columna("total_ventas", "Total", "moneda"));
            columnas.add(columna("precio_promedio", "Precio promedio", "moneda"));
            agregados.put("total_ventas", round(sumarColumna(filas, "total_ventas")));
            agregarRango(agregados, desde, hasta);
        } else if ("reparticion_metodos_pago".equals(id)) {
            filas.addAll(filasMetodosPago(empresaId, desde, hasta));
            columnas.add(columna("metodo_pago", "Metodo de pago", "texto"));
            columnas.add(columna("cantidad_transacciones", "Transacciones", "entero"));
            columnas.add(columna("total_ventas", "Total", "moneda"));
            agregados.put("total_ventas", round(sumarColumna(filas, "total_ventas")));
            agregarRango(agregados, desde, hasta);
        } else if ("comparar_periodos".equals(id)) {
            filas.addAll(filasCompararPeriodos(empresaId, desde, hasta));
            columnas.add(columna("periodo", "Periodo", "texto"));
            columnas.add(columna("cantidad_ventas", "Cantidad de ventas", "entero"));
            columnas.add(columna("total_ventas", "Total de ventas", "moneda"));
            columnas.add(columna("crecimiento_pct", "Crecimiento %", "decimal"));
            if (!filas.isEmpty()) {
                agregados.put("crecimiento_pct", filas.get(0).get("crecimiento_pct"));
            }
            agregarRango(agregados, desde, hasta);
        } else {
            id = "resumen_ventas";
            filas.addAll(filasResumenVentasPeriodo(empresaId, desde, hasta));
            columnas.add(columna("sucursal", "Sucursal", "texto"));
            columnas.add(columna("cantidad_ventas", "Cantidad de ventas", "entero"));
            columnas.add(columna("total_ventas", "Total de ventas", "moneda"));
            columnas.add(columna("ticket_promedio", "Ticket promedio", "moneda"));
            int cantidad = aInt(sumarColumna(filas, "cantidad_ventas"));
            double total = sumarColumna(filas, "total_ventas");
            agregados.put("cantidad_ventas", cantidad);
            agregados.put("total_ventas", round(total));
            agregados.put("ticket_promedio", cantidad == 0 ? 0 : round(total / cantidad));
            agregarRango(agregados, desde, hasta);
        }
        ReporteDtos.RespuestaReporte respuesta = new ReporteDtos.RespuestaReporte();
        respuesta.setIdReporte(UUID.randomUUID().toString());
        respuesta.setTitulo(interpretacion.getEspecificacion().getTitulo());
        respuesta.setIdentificadorPlantilla(id);
        respuesta.setEstado("listo");
        respuesta.setEspecificacion(interpretacion.getEspecificacion());
        respuesta.setFilas(filas);
        respuesta.setAgregados(agregados);
        respuesta.setAdvertencias(interpretacion.getAdvertencias());
        respuesta.setFechaGeneracion(LocalDate.now());
        respuesta.setColumnas(columnas);
        return respuesta;
    }

    // ---------------------------------------------------------------------------------------------
    // Predicciones
    // ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ReporteDtos.ProductoAbastecimiento> abastecimiento(Usuario usuario, Integer idSucursal) {
        if (idSucursal == null) {
            throw ApiException.badRequest("Debe indicar la sucursal.");
        }
        Sucursal sucursal = sucursalRepository.findById(idSucursal)
                .orElseThrow(() -> ApiException.notFound("Sucursal no encontrada."));
        empresaAccess.requireEmpresa(usuario, sucursal.getEmpresa().getIdEmpresa());
        empresaAccess.requirePlanFeature(usuario, sucursal.getEmpresa().getIdEmpresa(), "predicciones");
        LocalDateTime desde = LocalDate.now().minusDays(30).atStartOfDay();
        List<ReporteDtos.ProductoAbastecimiento> result = new ArrayList<>();
        for (Stock stock : stockRepository.findByIdSucursal(idSucursal)) {
            Producto producto = stock.getProducto();
            if (producto == null) {
                continue;
            }
            Number vendido = (Number) entityManager.createQuery("""
                    select coalesce(sum(d.cantidad), 0)
                    from DetalleVenta d, Venta v, CajaSesion cs
                    join cs.caja c
                    join c.sucursal s
                    where d.venta = v
                      and v.idCajaSesion = cs.idCajaSesion
                      and s.idSucursal = :idSucursal
                      and d.idProducto = :idProducto
                      and v.fecha >= :desde
                      and v.estado <> 'ANULADA'
                    """)
                    .setParameter("idSucursal", idSucursal)
                    .setParameter("idProducto", producto.getIdProducto())
                    .setParameter("desde", desde)
                    .getSingleResult();
            int vendido30 = vendido == null ? 0 : vendido.intValue();
            double promedio = vendido30 / 30.0;
            int prediccion = (int) Math.ceil(promedio * 30);
            ReporteDtos.ProductoAbastecimiento dto = new ReporteDtos.ProductoAbastecimiento();
            dto.setIdProducto(producto.getIdProducto());
            dto.setProducto(producto.getNombre());
            dto.setVendidoUltimos30Dias(vendido30);
            dto.setStockActual(actual);
            dto.setPromedioDiario(round(promedio));
            dto.setPrediccionProximos30Dias(prediccion);
            dto.setRecomendadoComprar(Math.max(0, prediccion - actual));
            result.add(dto);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ReporteDtos.RecomendacionResponse recomendar(Usuario usuario, List<Integer> ids) {
        empresaAccess.requireUsuarioActivo(usuario);
        if (ids == null || ids.isEmpty()) {
            throw ApiException.badRequest("Debe enviar al menos un producto.");
        }
        List<Producto> origen = productoRepository.findByIdProductoIn(ids);
        if (origen.isEmpty()) {
            throw ApiException.notFound("Productos no encontrados.");
        }
        Integer idEmpresa = origen.get(0).getIdEmpresa();
        if(origen.size()!=new java.util.HashSet<>(ids).size() || origen.stream().anyMatch(p -> !java.util.Objects.equals(idEmpresa,p.getIdEmpresa()))) throw ApiException.notFound("Productos no encontrados en una misma empresa.");
        var scope=dataPolicy.require(usuario,idEmpresa,null,"product:read","inventory:read");
        dataPolicy.productFields(scope,"nombre","precio","unidad_medida","codigo_barra","id_subcategoria");
        empresaAccess.requirePlanFeature(usuario, idEmpresa, "predicciones");
        Integer idSub = origen.get(0).getSubcategoria() == null ? null : origen.get(0).getSubcategoria().getIdSubcategoria();
        List<Producto> candidatos = idSub == null
                ? productoRepository.findByIdEmpresa(idEmpresa)
                : productoRepository.findBySubcategoria_IdSubcategoria(idSub);
        List<Producto> elegidos = new ArrayList<>();
        for (Producto producto : candidatos) {
            if (ids.contains(producto.getIdProducto())
                    || !Boolean.TRUE.equals(producto.getActivo())
                    || !idEmpresa.equals(producto.getIdEmpresa())) {
                continue;
            }
            elegidos.add(producto);
            if (elegidos.size() >= 8) {
                break;
            }
        }
        Map<Integer, Integer> stockPorProducto = new HashMap<>();
        if (!elegidos.isEmpty()) {
            for (Object[] row : entityManager.createQuery("""
                    select st.producto.idProducto, sum(st.cantidad)
                    from Stock st
                    where st.producto.idProducto in :ids
                    group by st.producto.idProducto
                    """, Object[].class)
                    .setParameter("ids", elegidos.stream().map(Producto::getIdProducto).toList())
                    .getResultList()) {
                stockPorProducto.put(aInt(row[0]), aInt(row[1]));
            }
        }
        List<ReporteDtos.ProductoRecomendado> recs = new ArrayList<>();
        for (Producto producto : elegidos) {
            ReporteDtos.ProductoRecomendado dto = new ReporteDtos.ProductoRecomendado();
            dto.setIdProducto(producto.getIdProducto());
            dto.setNombre(producto.getNombre());
            dto.setUnidadMedida(producto.getUnidadMedida());
            dto.setPrecio(producto.getPrecio() == null ? 0 : producto.getPrecio().doubleValue());
            dto.setStock(stockPorProducto.getOrDefault(producto.getIdProducto(), 0));
            dto.setCodigo(String.valueOf(producto.getIdProducto()));
            dto.setCodigoBarras(producto.getCodigoBarra());
            recs.add(dto);
        }
        ReporteDtos.RecomendacionResponse response = new ReporteDtos.RecomendacionResponse();
        response.setProductosAnalizados(ids);
        response.setRecomendaciones(recs);
        return response;
    }

    // ---------------------------------------------------------------------------------------------
    // Reportes parametrizados
    // ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> ventasParametrizado(Usuario usuario, Integer empresaId, ReporteDtos.VentasParamRequest filtros) {
        empresaAccess.requirePlanFeature(usuario, empresaId, "reportes_estandar");
        empresaAccess.requireEmpresa(usuario, empresaId);
        Empresa empresa = empresaRepository.findById(empresaId).orElseThrow();
        LocalDateTime desde = (filtros.getFechaInicial() == null ? LocalDate.now().minusDays(30) : filtros.getFechaInicial()).atStartOfDay();
        LocalDateTime hasta = (filtros.getFechaFinal() == null ? LocalDate.now() : filtros.getFechaFinal()).atTime(LocalTime.MAX);
        String jpql = """
                select v from Venta v, CajaSesion cs
                join cs.caja c
                join c.sucursal s
                where v.idCajaSesion = cs.idCajaSesion
                  and s.empresa.idEmpresa = :idEmpresa
                  and v.fecha between :desde and :hasta
                  and v.estado <> 'ANULADA'
                """;
        if (filtros.getIdSucursal() != null) {
            jpql.append(" and s.idSucursal = :idSucursal");
            params.put("idSucursal", filtros.getIdSucursal());
        }
        if (filtros.getIdTipoVenta() != null) {
            jpql.append(" and v.tipoVenta.idTipoVenta = :idTipoVenta");
            params.put("idTipoVenta", filtros.getIdTipoVenta());
        }
        if (filtros.getIdUsuario() != null) {
            jpql.append(" and v.idUsuario = :idUsuario");
            params.put("idUsuario", filtros.getIdUsuario());
        }
        if (filtros.getIdMetodoPago() != null) {
            jpql.append(" and exists (select 1 from VentaPago vp where vp.idVenta = v.idVenta and vp.idMetodoPago = :idMetodoPago)");
            params.put("idMetodoPago", filtros.getIdMetodoPago());
        }
        if (filtros.getIdProducto() != null) {
            jpql.append(" and exists (select 1 from DetalleVenta dp where dp.venta = v and dp.idProducto = :idProducto)");
            params.put("idProducto", filtros.getIdProducto());
        }
        jpql.append(" order by v.fecha, v.idVenta");
        List<Venta> ventas = consulta(jpql.toString(), Venta.class, params).getResultList();

        List<Integer> idsVenta = ventas.stream().map(Venta::getIdVenta).toList();
        Map<Integer, Integer> unidades = unidadesPorVenta(idsVenta, filtros.getIdProducto());
        Map<Integer, String> metodos = metodosPagoPorVenta(idsVenta);
        Map<Integer, String> personal = nombresUsuarios(ventas.stream().map(Venta::getIdUsuario).toList());

        List<Map<String, Object>> detalle = new ArrayList<>();
        double total = 0;
        int productosVendidos = 0;
        for (Venta venta : ventas) {
            int unidadesVenta = unidades.getOrDefault(venta.getIdVenta(), 0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id_venta", venta.getIdVenta());
            row.put("numero_venta", String.valueOf(venta.getIdVenta()));
            row.put("fecha_hora", venta.getFecha() == null ? "" : venta.getFecha().format(FECHA_HORA));
            row.put("id_usuario", venta.getIdUsuario());
            row.put("personal", personal.getOrDefault(venta.getIdUsuario(), "Usuario " + venta.getIdUsuario()));
            row.put("tipo", venta.getTipoVenta() == null ? null : venta.getTipoVenta().getNombre());
            row.put("metodo_pago", metodos.getOrDefault(venta.getIdVenta(), METODO_CREDITO));
            row.put("productos", unidadesVenta);
            row.put("total", round(aDouble(venta.getTotal())));
            detalle.add(row);
            total += aDouble(venta.getTotal());
            productosVendidos += unidadesVenta;
        }
        Map<String, Object> aplicados = new LinkedHashMap<>();
        aplicados.put("periodo", rango[0] + " / " + rango[1]);
        aplicados.put("sucursal", textoFiltro(filtros.getIdSucursal(), "todas"));
        aplicados.put("tipo_venta", textoFiltro(filtros.getIdTipoVenta(), "todos"));
        aplicados.put("metodo_pago", textoFiltro(filtros.getIdMetodoPago(), "todos"));
        aplicados.put("producto", textoFiltro(filtros.getIdProducto(), "todos"));
        aplicados.put("personal", textoFiltro(filtros.getIdUsuario(), "todos"));
        return respuestaParametrizada(empresa, aplicados, List.of(
                indicador("cantidad_ventas", "Cantidad de ventas", detalle.size()),
                indicador("monto_total", "Monto total", round(total)),
                indicador("productos_vendidos",
                        filtros.getIdProducto() == null ? "Productos vendidos (unidades)" : "Unidades del producto vendidas",
                        productosVendidos)),
                detalle);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> inventarioParametrizado(Usuario usuario, Integer empresaId, ReporteDtos.InventarioParamRequest filtros) {
        empresaAccess.requirePlanFeature(usuario, empresaId, "reportes_estandar");
        empresaAccess.requireEmpresa(usuario, empresaId);
        Empresa empresa = empresaRepository.findById(empresaId).orElseThrow();
        Map<String, Object> inventario = movimientosInventario(usuario, empresaId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sucursales = inventario.get("sucursales") instanceof List<?> lista
                ? (List<Map<String, Object>>) lista
                : new ArrayList<>();
        List<Map<String, Object>> sucursalesFiltradas = new ArrayList<>();
        int movimientosRestantes = 0;
        for (Map<String, Object> sucursal : sucursales) {
            if (!mismoId(sucursal.get("id_sucursal"), filtros.getIdSucursal())) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> movimientos = sucursal.get("movimientos") instanceof List<?> listaMovs
                    ? (List<Map<String, Object>>) listaMovs
                    : new ArrayList<>();
            List<Map<String, Object>> filtrados = new ArrayList<>();
            int entradas = 0;
            int salidas = 0;
            for (Map<String, Object> mov : movimientos) {
                if (!fechaDentro(mov.get("fecha"), filtros.getFechaInicial(), filtros.getFechaFinal())) {
                    continue;
                }
                if (!mismoId(mov.get("id_producto"), filtros.getIdProducto())) {
                    continue;
                }
                if (!mismoId(mov.get("id_tipo_movimiento"), filtros.getIdTipoMovimiento())) {
                    continue;
                }
                if (!mismoId(mov.get("id_categoria"), filtros.getIdCategoriaProducto())) {
                    continue;
                }
                filtrados.add(mov);
                int cantidad = aInt(mov.get("cantidad"));
                if ("ENTRADA".equalsIgnoreCase(String.valueOf(mov.get("direccion")))) {
                    entradas += cantidad;
                } else {
                    salidas += cantidad;
                }
            }
            sucursal.put("movimientos", filtrados);
            sucursal.put("resumen_sucursal", Map.of("total_entradas", entradas, "total_salidas", salidas));
            sucursalesFiltradas.add(sucursal);
            movimientosRestantes += filtrados.size();
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id_empresa", empresaId);
        response.put("empresa", empresa.getNombre());
        response.put("fecha_generacion", LocalDateTime.now().toString());
        response.put("filtros_aplicados", Map.of(
                "periodo", String.valueOf(filtros.getFechaInicial()) + " / " + filtros.getFechaFinal(),
                "sucursal", filtros.getIdSucursal() == null ? "todas" : String.valueOf(filtros.getIdSucursal()),
                "tipo_movimiento", filtros.getIdTipoMovimiento() == null ? "todos" : String.valueOf(filtros.getIdTipoMovimiento()),
                "producto", filtros.getIdProducto() == null ? "todos" : String.valueOf(filtros.getIdProducto()),
                "categoria", filtros.getIdCategoriaProducto() == null ? "todas" : String.valueOf(filtros.getIdCategoriaProducto())));
        response.put("resumen_gerencial", List.of(Map.of("indicador", "movimientos", "valor", movimientosRestantes)));
        response.put("detalle_analitico", sucursalesFiltradas);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> cajasParametrizado(Usuario usuario, Integer empresaId, ReporteDtos.CajasParamRequest filtros) {
        empresaAccess.requirePlanFeature(usuario, empresaId, "reportes_estandar");
        empresaAccess.requireEmpresa(usuario, empresaId);
        Empresa empresa = empresaRepository.findById(empresaId).orElseThrow();
        Map<String, Object> data = movimientosCaja(usuario, empresaId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sucursales = data.get("sucursales") instanceof List<?> lista
                ? (List<Map<String, Object>>) lista
                : new ArrayList<>();
        List<Map<String, Object>> sucursalesFiltradas = new ArrayList<>();
        int totalMovs = 0;
        double ingresos = 0;
        double egresos = 0;
        String estadoPedido = filtros.getEstadoSesion() == null ? "" : filtros.getEstadoSesion().trim();
        for (Map<String, Object> sucursal : sucursales) {
            if (!mismoId(sucursal.get("id_sucursal"), filtros.getIdSucursal())) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> movimientos = sucursal.get("movimientos") instanceof List<?> listaMovs
                    ? (List<Map<String, Object>>) listaMovs
                    : new ArrayList<>();
            List<Map<String, Object>> filtrados = new ArrayList<>();
            double ing = 0;
            double egr = 0;
            for (Map<String, Object> mov : movimientos) {
                if (!mismoId(mov.get("id_caja"), filtros.getIdCaja())) {
                    continue;
                }
                if (!mismoId(mov.get("id_tipo_movimiento_caja"), filtros.getIdTipoMovimientoCaja())) {
                    continue;
                }
                if (!estadoPedido.isEmpty()) {
                    String estado = mov.get("estado_sesion") == null ? "" : String.valueOf(mov.get("estado_sesion")).trim();
                    if (!estadoPedido.equalsIgnoreCase(estado)) {
                        continue;
                    }
                }
                if (!fechaDentro(mov.get("hora"), filtros.getFechaInicial(), filtros.getFechaFinal())) {
                    continue;
                }
                filtrados.add(mov);
                double monto = aDouble(mov.get("monto"));
                if (String.valueOf(mov.get("tipo")).toUpperCase(Locale.ROOT).contains("INGRESO")) {
                    ing += monto;
                } else {
                    egr += monto;
                }
            }
            sucursal.put("movimientos", filtrados);
            sucursal.put("resumen_sucursal", Map.of(
                    "total_movimientos", filtrados.size(),
                    "total_ingresos", round(ing),
                    "total_egresos", round(egr)));
            sucursalesFiltradas.add(sucursal);
            totalMovs += filtrados.size();
            ingresos += ing;
            egresos += egr;
        }
        Map<String, Object> totales = new LinkedHashMap<>();
        totales.put("total_movimientos", totalMovs);
        totales.put("total_ingresos", round(ingresos));
        totales.put("total_egresos", round(egresos));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id_empresa", empresaId);
        response.put("empresa", empresa.getNombre());
        response.put("fecha_generacion", LocalDateTime.now().toString());
        response.put("filtros_aplicados", Map.of(
                "periodo", String.valueOf(filtros.getFechaInicial()) + " / " + filtros.getFechaFinal(),
                "sucursal", filtros.getIdSucursal() == null ? "todas" : String.valueOf(filtros.getIdSucursal()),
                "caja", filtros.getIdCaja() == null ? "todas" : String.valueOf(filtros.getIdCaja()),
                "tipo_movimiento", filtros.getIdTipoMovimientoCaja() == null ? "todos" : String.valueOf(filtros.getIdTipoMovimientoCaja()),
                "estado_sesion", filtros.getEstadoSesion() == null ? "todos" : filtros.getEstadoSesion()));
        response.put("resumen_gerencial", List.of(Map.of("indicador", "movimientos", "valor", totales)));
        response.put("detalle_analitico", sucursalesFiltradas);
        return response;
    }

    private List<Venta> ventasDeSucursal(Integer idSucursal, LocalDateTime desde, LocalDateTime hasta) {
        return entityManager.createQuery("""
                select v from Venta v, CajaSesion cs
                join cs.caja c
                where v.idCajaSesion = cs.idCajaSesion
                  and c.sucursal.idSucursal = :idSucursal
                  and v.fecha between :desde and :hasta
                  and v.estado <> 'ANULADA'
                """, Venta.class)
                .setParameter("idSucursal", idSucursal)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta)
                .getResultList();
    }

    private List<DetalleVenta> detalles(Integer idVenta) {
        return entityManager.createQuery("select d from DetalleVenta d where d.venta.idVenta = :id", DetalleVenta.class)
                .setParameter("id", idVenta)
                .getResultList();
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Stock stock : stocks) {
            if (!nombresSucursal.containsKey(stock.getIdSucursal())) {
                continue;
            }
            String estado = EstadoStock.de(stock.getCantidad(), stock.getStockMinimo(), stock.getStockMaximo());
            if (soloAlertas && !EstadoStock.esAlerta(estado)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id_sucursal", stock.getIdSucursal());
            row.put("sucursal", nombresSucursal.get(stock.getIdSucursal()));
            row.put("id_producto", stock.getProducto().getIdProducto());
            row.put("producto", stock.getProducto().getNombre());
            row.put("stock_actual", stock.getCantidad() == null ? 0 : stock.getCantidad());
            row.put("stock_minimo", stock.getStockMinimo());
            row.put("stock_maximo", stock.getStockMaximo());
            row.put("estado", estado);
            filas.add(row);
        }
        return filas;
    }

    private List<Map<String, Object>> filasStockFiltrado(Integer empresaId, Integer idSucursal, Integer idProducto, Integer idCategoria) {
        StringBuilder jpql = new StringBuilder("""
                select st.idSucursal, st.cantidad, st.stockMinimo, st.stockMaximo
                from Stock st
                join st.producto p
                left join p.subcategoria sub
                left join sub.categoriaProducto cat
                where p.idEmpresa = :idEmpresa
                """);
        Map<String, Object> params = new HashMap<>();
        params.put("idEmpresa", empresaId);
        if (idSucursal != null) {
            jpql.append(" and st.idSucursal = :idSucursal");
            params.put("idSucursal", idSucursal);
        }
        if (idProducto != null) {
            jpql.append(" and p.idProducto = :idProducto");
            params.put("idProducto", idProducto);
        }
        if (idCategoria != null) {
            jpql.append(" and cat.idCategoriaProducto = :idCategoria");
            params.put("idCategoria", idCategoria);
        }
        Set<Integer> sucursalesEmpresa = nombresSucursales(empresaId).keySet();
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Object[] row : consulta(jpql.toString(), Object[].class, params).getResultList()) {
            if (!sucursalesEmpresa.contains(aInt(row[0]))) {
                continue;
            }
            Integer cantidad = row[1] == null ? 0 : aInt(row[1]);
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("stock_actual", cantidad);
            fila.put("estado", EstadoStock.de(cantidad, (Integer) row[2], (Integer) row[3]));
            filas.add(fila);
        }
        return filas;
    }

    private List<Map<String, Object>> filasMovimientosInventario(Integer empresaId, LocalDate desde, LocalDate hasta,
            Integer idSucursal, Integer idTipo, Integer idProducto, Integer idCategoria) {
        Map<Integer, String> nombresSucursal = nombresSucursales(empresaId);
        StringBuilder jpql = new StringBuilder("""
                select m from MovimientoInventario m
                join fetch m.producto p
                join fetch m.tipoMovimiento t
                left join fetch p.subcategoria sub
                left join fetch sub.categoriaProducto cat
                where p.idEmpresa = :idEmpresa
                  and m.fechaMovimiento between :desde and :hasta
                """);
        Map<String, Object> params = paramsRango(empresaId, desde, hasta);
        if (idSucursal != null) {
            jpql.append(" and m.idSucursal = :idSucursal");
            params.put("idSucursal", idSucursal);
        }
        if (idTipo != null) {
            jpql.append(" and t.idTipoMovimiento = :idTipo");
            params.put("idTipo", idTipo);
        }
        if (idProducto != null) {
            jpql.append(" and p.idProducto = :idProducto");
            params.put("idProducto", idProducto);
        }
        if (idCategoria != null) {
            jpql.append(" and cat.idCategoriaProducto = :idCategoria");
            params.put("idCategoria", idCategoria);
        }
        jpql.append(" order by m.fechaMovimiento desc, m.idMovimientoInventario desc");
        List<Map<String, Object>> filas = new ArrayList<>();
        for (MovimientoInventario mov : consulta(jpql.toString(), MovimientoInventario.class, params).getResultList()) {
            if (!nombresSucursal.containsKey(mov.getIdSucursal())) {
                continue;
            }
            Producto producto = mov.getProducto();
            var categoria = producto.getSubcategoria() == null ? null : producto.getSubcategoria().getCategoriaProducto();
            String direccion = mov.getTipoMovimiento().getDireccion() == null ? "" : mov.getTipoMovimiento().getDireccion();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id_movimiento_inventario", mov.getIdMovimientoInventario());
            row.put("fecha", mov.getFechaMovimiento() == null ? null : mov.getFechaMovimiento().toLocalDate().toString());
            row.put("fecha_hora", mov.getFechaMovimiento() == null ? "" : mov.getFechaMovimiento().format(FECHA_HORA));
            row.put("id_sucursal", mov.getIdSucursal());
            row.put("sucursal", nombresSucursal.get(mov.getIdSucursal()));
            row.put("id_tipo_movimiento", mov.getTipoMovimiento().getIdTipoMovimiento());
            row.put("tipo_movimiento", mov.getTipoMovimiento().getNombre());
            row.put("direccion", direccion.toUpperCase(Locale.ROOT));
            row.put("id_producto", producto.getIdProducto());
            row.put("producto", producto.getNombre());
            row.put("id_categoria", categoria == null ? null : categoria.getIdCategoriaProducto());
            row.put("categoria", categoria == null ? "" : categoria.getNombre());
            row.put("cantidad", mov.getCantidad() == null ? 0 : mov.getCantidad());
            filas.add(row);
        }
        return filas;
    }

    private List<Map<String, Object>> filasMovimientosCaja(Integer empresaId, LocalDate desde, LocalDate hasta,
            Integer idSucursal, Integer idCaja, Integer idTipo, String estadoSesion) {
        StringBuilder jpql = new StringBuilder("""
                select s.idSucursal, s.nombre, mc.idMovimientoCaja, mc.fecha, c.idCaja, c.nombre,
                       t.idTipoMovimientoCaja, t.nombre, mc.concepto, mc.monto, cs.estado
                from MovimientoCaja mc
                join mc.cajaSesion cs
                join cs.caja c
                join c.sucursal s
                join mc.tipoMovimientoCaja t
                where s.empresa.idEmpresa = :idEmpresa
                  and mc.fecha between :desde and :hasta
                """);
        Map<String, Object> params = paramsRango(empresaId, desde, hasta);
        if (idSucursal != null) {
            jpql.append(" and s.idSucursal = :idSucursal");
            params.put("idSucursal", idSucursal);
        }
        if (idCaja != null) {
            jpql.append(" and c.idCaja = :idCaja");
            params.put("idCaja", idCaja);
        }
        if (idTipo != null) {
            jpql.append(" and t.idTipoMovimientoCaja = :idTipo");
            params.put("idTipo", idTipo);
        }
        if (estadoSesion != null) {
            jpql.append(" and upper(cs.estado) = :estado");
            params.put("estado", estadoSesion.toUpperCase(Locale.ROOT));
        }
        jpql.append(" order by mc.fecha desc, mc.idMovimientoCaja desc");
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Object[] row : consulta(jpql.toString(), Object[].class, params).getResultList()) {
            LocalDateTime fecha = (LocalDateTime) row[3];
            String tipo = String.valueOf(row[7]);
            Map<String, Object> mov = new LinkedHashMap<>();
            mov.put("id_movimiento_caja", row[2]);
            mov.put("fecha_hora", fecha == null ? "" : fecha.format(FECHA_HORA));
            mov.put("hora", fecha == null ? "" : fecha.format(HORA));
            mov.put("id_sucursal", row[0]);
            mov.put("sucursal", row[1]);
            mov.put("id_caja", row[4]);
            mov.put("caja", row[5]);
            mov.put("id_tipo_movimiento_caja", row[6]);
            mov.put("tipo", tipo);
            mov.put("estado_sesion", row[10]);
            mov.put("concepto", row[8]);
            mov.put("monto", round(aDouble(row[9])));
            mov.put("signo", SignoMovimientoCaja.signo(tipo));
            filas.add(mov);
        }
        return filas;
    }

    /** Sesiones que estuvieron abiertas en algun momento del rango, con los totales de su cierre. */
    private List<Map<String, Object>> filasSesionesCaja(Integer empresaId, LocalDate desde, LocalDate hasta,
            Integer idSucursal, Integer idCaja, String estadoSesion) {
        StringBuilder jpql = new StringBuilder("""
                select s.idSucursal, s.nombre, c.idCaja, c.nombre, cs.idCajaSesion, cs.estado,
                       cs.fechaApertura, cs.fechaCierre, cs.montoInicial
                from CajaSesion cs
                join cs.caja c
                join c.sucursal s
                where s.empresa.idEmpresa = :idEmpresa
                  and cs.fechaApertura <= :hasta
                  and (cs.fechaCierre is null or cs.fechaCierre >= :desde)
                """);
        Map<String, Object> params = paramsRango(empresaId, desde, hasta);
        if (idSucursal != null) {
            jpql.append(" and s.idSucursal = :idSucursal");
            params.put("idSucursal", idSucursal);
        }
        if (idCaja != null) {
            jpql.append(" and c.idCaja = :idCaja");
            params.put("idCaja", idCaja);
        }
        if (estadoSesion != null) {
            jpql.append(" and upper(cs.estado) = :estado");
            params.put("estado", estadoSesion.toUpperCase(Locale.ROOT));
        }
        jpql.append(" order by s.nombre, c.nombre, cs.fechaApertura");
        List<Object[]> rows = consulta(jpql.toString(), Object[].class, params).getResultList();

        Map<Integer, Object[]> cierres = new HashMap<>();
        porLotes(rows.stream().map(r -> aInt(r[4])).toList(), lote -> {
            for (Object[] row : entityManager.createQuery("""
                    select d.cajaSesion.idCajaSesion, sum(d.montoEsperado), sum(d.montoReal), sum(d.diferencia)
                    from CajaCierreDetalle d
                    where d.cajaSesion.idCajaSesion in :ids
                    group by d.cajaSesion.idCajaSesion
                    """, Object[].class).setParameter("ids", lote).getResultList()) {
                cierres.put(aInt(row[0]), row);
            }
        });

        List<Map<String, Object>> filas = new ArrayList<>();
        for (Object[] row : rows) {
            Object[] cierre = cierres.get(aInt(row[4]));
            Map<String, Object> sesion = new LinkedHashMap<>();
            sesion.put("id_sucursal", row[0]);
            sesion.put("sucursal", row[1]);
            sesion.put("id_caja", row[2]);
            sesion.put("caja", row[3]);
            sesion.put("id_caja_sesion", row[4]);
            sesion.put("estado", row[5]);
            sesion.put("apertura", row[6] == null ? "" : ((LocalDateTime) row[6]).format(FECHA_HORA));
            sesion.put("cierre", row[7] == null ? null : ((LocalDateTime) row[7]).format(FECHA_HORA));
            sesion.put("monto_inicial", round(aDouble(row[8])));
            sesion.put("monto_esperado", cierre == null ? 0 : round(aDouble(cierre[1])));
            sesion.put("monto_declarado", cierre == null ? 0 : round(aDouble(cierre[2])));
            sesion.put("diferencia", cierre == null ? 0 : round(aDouble(cierre[3])));
            filas.add(sesion);
        }
        return filas;
    }

    private Map<Integer, Object[]> totalesPorSucursal(Integer empresaId, LocalDate desde, LocalDate hasta) {
        Map<Integer, Object[]> mapa = new LinkedHashMap<>();
        for (Object[] row : consulta("select s.idSucursal, s.nombre, count(v), sum(v.total)" + FROM_VENTAS
                + " group by s.idSucursal, s.nombre", Object[].class, paramsRango(empresaId, desde, hasta)).getResultList()) {
            mapa.put(aInt(row[0]), new Object[] { row[1], aInt(row[2]), aDouble(row[3]) });
        }
        return mapa;
    }

    private double[] totalesEmpresa(Integer empresaId, LocalDate desde, LocalDate hasta) {
        Object[] row = consulta("select count(v), sum(v.total)" + FROM_VENTAS, Object[].class,
                paramsRango(empresaId, desde, hasta)).getSingleResult();
        return new double[] { aInt(row[0]), aDouble(row[1]) };
    }

    private List<Map<String, Object>> filasResumenVentasPeriodo(Integer empresaId, LocalDate desde, LocalDate hasta) {
        Map<Integer, Object[]> porSucursal = totalesPorSucursal(empresaId, desde, hasta);
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Sucursal sucursal : sucursales(empresaId)) {
            Object[] vals = porSucursal.get(sucursal.getIdSucursal());
            int cantidad = vals == null ? 0 : aInt(vals[1]);
            double monto = vals == null ? 0 : aDouble(vals[2]);
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("sucursal", sucursal.getNombre());
            fila.put("cantidad_ventas", cantidad);
            fila.put("total_ventas", round(monto));
            fila.put("ticket_promedio", cantidad == 0 ? 0 : round(monto / cantidad));
            filas.add(fila);
        }
        return filas;
    }

    private List<Map<String, Object>> filasVentasPorSucursal(Integer empresaId, LocalDate desde, LocalDate hasta) {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Object[] vals : totalesPorSucursal(empresaId, desde, hasta).values()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("sucursal", vals[0]);
            fila.put("total_ventas", round(aDouble(vals[2])));
            fila.put("cantidad_ventas", aInt(vals[1]));
            filas.add(fila);
        }
        filas.sort((a, b) -> Double.compare(aDouble(b.get("total_ventas")), aDouble(a.get("total_ventas"))));
        return filas;
    }

    /** Ventas agrupadas por producto (por id, no por nombre). Top por cantidad o por monto. */
    private List<Map<String, Object>> filasPorProducto(Integer empresaId, LocalDate desde, LocalDate hasta, int limite, boolean porMonto) {
        String orden = porMonto ? "sum(d.total) desc" : "sum(d.cantidad) desc";
        List<Object[]> rows = consulta("select p.idProducto, p.nombre, cat.nombre, sum(d.cantidad), sum(d.total)" + FROM_LINEAS
                + " group by p.idProducto, p.nombre, cat.nombre order by " + orden + ", p.nombre",
                Object[].class, paramsRango(empresaId, desde, hasta))
                .setMaxResults(limite)
                .getResultList();
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Object[] row : rows) {
            int cantidad = aInt(row[3]);
            double total = aDouble(row[4]);
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("producto", row[1]);
            if (porMonto) {
                fila.put("categoria_producto", row[2] == null ? "" : row[2]);
            }
            fila.put("cantidad_vendida", cantidad);
            fila.put("total_ventas", round(total));
            if (porMonto) {
                fila.put("precio_promedio", cantidad == 0 ? 0 : round(total / cantidad));
            }
            filas.add(fila);
        }
        return filas;
    }

    /** Reparte cada venta segun lo pagado con cada metodo; las ventas a credito (sin pagos) van aparte. */
    private List<Map<String, Object>> filasMetodosPago(Integer empresaId, LocalDate desde, LocalDate hasta) {
        Map<String, Object> params = paramsRango(empresaId, desde, hasta);
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Object[] row : consulta("select m.nombre, count(vp), sum(vp.monto)" + FROM_VENTAS.replaceFirst(
                "from Venta v,", "from VentaPago vp, MetodoPago m, Venta v,")
                + " and vp.idVenta = v.idVenta and vp.idMetodoPago = m.idMetodoPago"
                + " group by m.nombre order by sum(vp.monto) desc", Object[].class, params).getResultList()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("metodo_pago", row[0]);
            fila.put("cantidad_transacciones", aInt(row[1]));
            fila.put("total_ventas", round(aDouble(row[2])));
            filas.add(fila);
        }
        Object[] credito = consulta("select count(v), sum(v.total)" + FROM_VENTAS
                + " and not exists (select 1 from VentaPago vp where vp.idVenta = v.idVenta)",
                Object[].class, params).getSingleResult();
        if (aInt(credito[0]) > 0) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("metodo_pago", METODO_CREDITO);
            fila.put("cantidad_transacciones", aInt(credito[0]));
            fila.put("total_ventas", round(aDouble(credito[1])));
            filas.add(fila);
        }
        return filas;
    }

    private List<Map<String, Object>> filasCompararPeriodos(Integer empresaId, LocalDate desde, LocalDate hasta) {
        long dias = Math.max(1, hasta.toEpochDay() - desde.toEpochDay() + 1);
        LocalDate hastaAnterior = desde.minusDays(1);
        LocalDate desdeAnterior = hastaAnterior.minusDays(dias - 1);
        double[] actual = totalesEmpresa(empresaId, desde, hasta);
        double[] anterior = totalesEmpresa(empresaId, desdeAnterior, hastaAnterior);
        Double crecimiento = anterior[1] == 0
                ? (actual[1] == 0 ? Double.valueOf(0) : null)
                : Double.valueOf(round((actual[1] - anterior[1]) / anterior[1] * 100.0));
        List<Map<String, Object>> filas = new ArrayList<>();
        filas.add(filaPeriodo("Periodo actual", desde, hasta, actual, crecimiento));
        filas.add(filaPeriodo("Periodo anterior", desdeAnterior, hastaAnterior, anterior, null));
        return filas;
    }

    private Map<String, Object> filaPeriodo(String nombre, LocalDate desde, LocalDate hasta, double[] totales, Double crecimiento) {
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("periodo", nombre + " (" + desde + " / " + hasta + ")");
        fila.put("cantidad_ventas", (int) totales[0]);
        fila.put("total_ventas", round(totales[1]));
        // Sin ventas en el periodo anterior el crecimiento no se puede calcular (no es 100%).
        fila.put("crecimiento_pct", crecimiento);
        return fila;
    }

    private List<Map<String, Object>> filasDetalleVentas(Integer empresaId, LocalDate desde, LocalDate hasta) {
        List<Object[]> rows = ventasConSucursal(empresaId, desde, hasta);
        Map<Integer, String> metodos = metodosPagoPorVenta(rows.stream().map(r -> ((Venta) r[0]).getIdVenta()).toList());
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Object[] r : rows) {
            Venta venta = (Venta) r[0];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id_venta", venta.getIdVenta());
            row.put("fecha", venta.getFecha() == null ? "" : venta.getFecha().format(FECHA_HORA));
            row.put("sucursal", r[2]);
            row.put("cliente", venta.getIdCliente() == null ? "" : "Cliente " + venta.getIdCliente());
            row.put("metodo_pago", metodos.getOrDefault(venta.getIdVenta(), METODO_CREDITO));
            row.put("total", round(aDouble(venta.getTotal())));
            filas.add(row);
        }
        return filas;
    }

    private Map<Integer, Integer> unidadesPorVenta(List<Integer> idsVenta, Integer idProducto) {
        Map<Integer, Integer> unidades = new HashMap<>();
        porLotes(idsVenta, lote -> {
            TypedQuery<Object[]> query = entityManager.createQuery(
                    "select d.venta.idVenta, sum(d.cantidad) from DetalleVenta d where d.venta.idVenta in :ids"
                            + (idProducto == null ? "" : " and d.idProducto = :idProducto")
                            + " group by d.venta.idVenta", Object[].class)
                    .setParameter("ids", lote);
            if (idProducto != null) {
                query.setParameter("idProducto", idProducto);
            }
            for (Object[] row : query.getResultList()) {
                unidades.put(aInt(row[0]), aInt(row[1]));
            }
        });
        return unidades;
    }

    private Map<Integer, String> metodosPagoPorVenta(List<Integer> idsVenta) {
        Map<Integer, Set<String>> nombres = new HashMap<>();
        porLotes(idsVenta, lote -> {
            for (Object[] row : entityManager.createQuery("""
                    select vp.idVenta, m.nombre
                    from VentaPago vp, MetodoPago m
                    where m.idMetodoPago = vp.idMetodoPago
                      and vp.idVenta in :ids
                    """, Object[].class).setParameter("ids", lote).getResultList()) {
                nombres.computeIfAbsent(aInt(row[0]), k -> new TreeSet<>()).add(String.valueOf(row[1]));
            }
        });
        Map<Integer, String> resultado = new HashMap<>();
        nombres.forEach((id, set) -> resultado.put(id, String.join(", ", set)));
        return resultado;
    }

    private Map<Integer, String> nombresUsuarios(Collection<Integer> idsUsuario) {
        Map<Integer, String> nombres = new HashMap<>();
        List<Integer> ids = new ArrayList<>(new LinkedHashSet<>(idsUsuario));
        ids.removeIf(java.util.Objects::isNull);
        porLotes(ids, lote -> {
            for (Object[] row : entityManager.createQuery("""
                    select u.idUsuario, pe.nombreCompleto, u.email
                    from Usuario u
                    left join u.persona pe
                    where u.idUsuario in :ids
                    """, Object[].class).setParameter("ids", lote).getResultList()) {
                String nombre = row[1] == null || String.valueOf(row[1]).isBlank() ? String.valueOf(row[2]) : String.valueOf(row[1]);
                nombres.put(aInt(row[0]), nombre);
            }
        });
        return nombres;
    }

    // ---------------------------------------------------------------------------------------------
    // Reglas y utilidades
    // ---------------------------------------------------------------------------------------------

    private void requireReporte(Usuario usuario, Integer empresaId, String funcionalidadPlan) {
        empresaAccess.requirePermiso(usuario, empresaId, PERMISO_REPORTES);
        empresaAccess.requirePlanFeature(usuario, empresaId, funcionalidadPlan);
    }

    private Empresa requireEmpresaExistente(Integer empresaId) {
        return empresaRepository.findById(empresaId).orElseThrow(() -> ApiException.notFound("La empresa no existe."));
    }

    private List<Sucursal> sucursales(Integer empresaId) {
        List<Sucursal> lista = new ArrayList<>(sucursalRepository.findByEmpresa_IdEmpresa(empresaId));
        lista.sort((a, b) -> String.valueOf(a.getNombre()).compareToIgnoreCase(String.valueOf(b.getNombre())));
        return lista;
    }

    private Map<Integer, String> nombresSucursales(Integer empresaId) {
        Map<Integer, String> mapa = new LinkedHashMap<>();
        for (Sucursal sucursal : sucursales(empresaId)) {
            mapa.put(sucursal.getIdSucursal(), sucursal.getNombre());
        }
        return mapa;
    }

    private static Map<Integer, List<Map<String, Object>>> agruparPorSucursal(List<Map<String, Object>> filas) {
        Map<Integer, List<Map<String, Object>>> grupos = new HashMap<>();
        for (Map<String, Object> fila : filas) {
            grupos.computeIfAbsent(aIntStatic(fila.get("id_sucursal")), k -> new ArrayList<>()).add(fila);
        }
        return grupos;
    }

    private Map<String, Object> resumenMontos(int cantidad, double monto, int unidades) {
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("total_ventas", cantidad);
        resumen.put("monto_vendido", round(monto));
        resumen.put("ticket_promedio", cantidad == 0 ? 0 : round(monto / cantidad));
        resumen.put("productos_vendidos", unidades);
        return resumen;
    }

    private Map<String, Object> resumenStock(List<Map<String, Object>> filas) {
        int bajo = 0;
        int sobre = 0;
        int agotados = 0;
        int disponible = 0;
        for (Map<String, Object> fila : filas) {
            String estado = String.valueOf(fila.get("estado"));
            if (EstadoStock.AGOTADO.equals(estado)) {
                agotados++;
            } else if (EstadoStock.BAJO.equals(estado)) {
                bajo++;
            } else if (EstadoStock.SOBRE.equals(estado)) {
                sobre++;
            }
            disponible += Math.max(0, aInt(fila.get("stock_actual")));
        }
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("total_productos", filas.size());
        resumen.put("productos_bajo_stock", bajo);
        resumen.put("productos_sobre_stock", sobre);
        resumen.put("productos_agotados", agotados);
        resumen.put("stock_disponible", disponible);
        return resumen;
    }

    private Map<String, Object> resumenCajasDe(List<Map<String, Object>> sesiones, List<Map<String, Object>> movimientos) {
        Set<Object> cajas = new HashSet<>();
        Set<Object> cajasAbiertas = new HashSet<>();
        for (Map<String, Object> sesion : sesiones) {
            cajas.add(sesion.get("id_caja"));
            if (esAbierta(sesion.get("estado"))) {
                cajasAbiertas.add(sesion.get("id_caja"));
            }
        }
        double[] ie = ingresosEgresos(movimientos);
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("total_cajas", cajas.size());
        resumen.put("cajas_abiertas", cajasAbiertas.size());
        resumen.put("cajas_cerradas", cajas.size() - cajasAbiertas.size());
        resumen.put("total_sesiones", sesiones.size());
        resumen.put("ingresos", round(ie[0]));
        resumen.put("egresos", round(ie[1]));
        resumen.put("flujo_neto", round(ie[0] - ie[1]));
        return resumen;
    }

    private Map<String, Object> resumenMovimientosCaja(int cantidad, double[] ie) {
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("total_movimientos", cantidad);
        resumen.put("total_ingresos", round(ie[0]));
        resumen.put("total_egresos", round(ie[1]));
        resumen.put("flujo_neto", round(ie[0] - ie[1]));
        return resumen;
    }

    /** [ingresos, egresos] con la misma regla de signo que usa el arqueo de caja. */
    private double[] ingresosEgresos(List<Map<String, Object>> movimientos) {
        double ingresos = 0;
        double egresos = 0;
        for (Map<String, Object> mov : movimientos) {
            int signo = aInt(mov.get("signo"));
            if (signo > 0) {
                ingresos += aDouble(mov.get("monto"));
            } else if (signo < 0) {
                egresos += aDouble(mov.get("monto"));
            }
        }
        return new double[] { ingresos, egresos };
    }

    /** [entradas, salidas] en unidades. */
    private int[] entradasSalidas(List<Map<String, Object>> movimientos) {
        int entradas = 0;
        int salidas = 0;
        for (Map<String, Object> mov : movimientos) {
            if ("ENTRADA".equalsIgnoreCase(String.valueOf(mov.get("direccion")))) {
                entradas += aInt(mov.get("cantidad"));
            } else {
                salidas += aInt(mov.get("cantidad"));
            }
        }
        return new int[] { entradas, salidas };
    }

    private static boolean esAbierta(Object estado) {
        return estado != null && String.valueOf(estado).trim().toUpperCase(Locale.ROOT).startsWith("ABIERT");
    }

    private Map<String, Object> respuestaParametrizada(Empresa empresa, Map<String, Object> aplicados,
            List<Map<String, Object>> indicadores, List<Map<String, Object>> detalle) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id_empresa", empresa.getIdEmpresa());
        response.put("empresa", empresa.getNombre());
        response.put("fecha_generacion", LocalDateTime.now().format(FECHA_HORA));
        response.put("filtros_aplicados", aplicados);
        response.put("resumen_gerencial", indicadores);
        response.put("detalle_analitico", detalle);
        return response;
    }

    private static Map<String, Object> indicador(String clave, String nombre, Object valor) {
        Map<String, Object> indicador = new LinkedHashMap<>();
        indicador.put("clave", clave);
        indicador.put("indicador", nombre);
        indicador.put("valor", valor);
        return indicador;
    }

    private static String textoFiltro(Integer valor, String todos) {
        return valor == null ? todos : String.valueOf(valor);
    }

    private static Map<String, Object> mapa(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put(k1, v1);
        mapa.put(k2, v2);
        return mapa;
    }

    private static Map<String, Object> paramsRango(Integer empresaId, LocalDate desde, LocalDate hasta) {
        Map<String, Object> params = new HashMap<>();
        params.put("idEmpresa", empresaId);
        params.put("desde", desde.atStartOfDay());
        params.put("hasta", hasta.atTime(LocalTime.MAX));
        return params;
    }

    private <T> TypedQuery<T> consulta(String jpql, Class<T> tipo, Map<String, Object> params) {
        TypedQuery<T> query = entityManager.createQuery(jpql, tipo);
        params.forEach(query::setParameter);
        return query;
    }

    private static void porLotes(List<Integer> ids, Consumer<List<Integer>> accion) {
        for (int i = 0; i < ids.size(); i += LOTE_IN) {
            accion.accept(ids.subList(i, Math.min(ids.size(), i + LOTE_IN)));
        }
    }

    /** Rango de los reportes parametrizados: por defecto los ultimos 30 dias. */
    private static LocalDate[] rangoFiltros(LocalDate inicial, LocalDate fin) {
        LocalDate hasta = fin == null ? LocalDate.now() : fin;
        LocalDate desde = inicial == null ? hasta.minusDays(29) : inicial;
        validarRango(desde, hasta);
        return new LocalDate[] { desde, hasta };
    }

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw ApiException.badRequest("La fecha inicial no puede ser posterior a la fecha final.");
        }
    }

    private ReporteDtos.Plantilla plantilla(
            String id, String nombre, String descripcion, List<String> metricas, List<String> dimensiones,
            String formato, Map<String, Object> filtros, List<String> etiquetas) {
        ReporteDtos.Plantilla plantilla = new ReporteDtos.Plantilla();
        plantilla.setIdentificador(id);
        plantilla.setNombre(nombre);
        plantilla.setDescripcion(descripcion);
        plantilla.setMetricas(metricas);
        plantilla.setDimensiones(dimensiones);
        plantilla.setFormato(formato);
        plantilla.setFiltrosPorDefecto(new LinkedHashMap<>(filtros));
        plantilla.setEtiquetas(etiquetas);
        return plantilla;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private ReporteDtos.Columna columna(String nombre, String etiqueta, String tipo) {
        ReporteDtos.Columna col = new ReporteDtos.Columna();
        col.setNombre(nombre);
        col.setEtiqueta(etiqueta);
        col.setTipo(tipo);
        return col;
    }

    private void agregarRango(Map<String, Object> agregados, LocalDate desde, LocalDate hasta) {
        agregados.put("desde", desde.toString());
        agregados.put("hasta", hasta.toString());
    }

    private LocalDate[] rangoPeriodo(Map<String, Object> filtros) {
        LocalDate hoy = LocalDate.now();
        String periodo = "";
        if (filtros != null && filtros.get("periodo") != null) {
            periodo = String.valueOf(filtros.get("periodo")).trim().toLowerCase(Locale.ROOT);
            periodo = periodo.replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u");
        }
        LocalDate desde = switch (periodo) {
            case "hoy" -> hoy;
            case "ultimos_7_dias" -> hoy.minusDays(6);
            case "ultimos_90_dias" -> hoy.minusDays(89);
            case "este_mes" -> hoy.withDayOfMonth(1);
            case "este_anio" -> hoy.withDayOfYear(1);
            // Periodo vacio o desconocido: el mismo valor por defecto que anuncian las plantillas.
            default -> hoy.minusDays(29);
        };
        return new LocalDate[] { desde, hoy };
    }

    private int topN(Map<String, Object> filtros) {
        if (filtros == null || filtros.get("top_n") == null) {
            return 10;
        }
        int valor = aInt(filtros.get("top_n"));
        if (valor <= 0) {
            try {
                valor = Integer.parseInt(String.valueOf(filtros.get("top_n")).trim());
            } catch (NumberFormatException ex) {
                valor = 10;
            }
        }
        return valor <= 0 ? 10 : Math.min(valor, 100);
    }

    private static int aIntStatic(Object valor) {
        return valor instanceof Number numero ? numero.intValue() : 0;
    }

    private int aInt(Object valor) {
        return aIntStatic(valor);
    }

    private double aDouble(Object valor) {
        if (valor instanceof Number numero) {
            return numero.doubleValue();
        }
        return 0;
    }

    private double sumarColumna(List<Map<String, Object>> filas, String clave) {
        double total = 0;
        for (Map<String, Object> fila : filas) {
            total += aDouble(fila.get(clave));
        }
        return total;
    }
}
