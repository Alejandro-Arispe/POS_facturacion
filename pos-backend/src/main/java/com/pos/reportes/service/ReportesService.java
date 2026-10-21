package com.pos.reportes.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.common.access.EmpresaAccess;
import com.pos.common.ai.GeminiClient;
import com.pos.common.exception.ApiException;
import com.pos.empresas.domain.Empresa;
import com.pos.empresas.domain.Sucursal;
import com.pos.empresas.repo.EmpresaRepository;
import com.pos.empresas.repo.SucursalRepository;
import com.pos.inventario.domain.MovimientoInventario;
import com.pos.inventario.domain.Stock;
import com.pos.inventario.repo.MovimientoInventarioRepository;
import com.pos.inventario.repo.StockRepository;
import com.pos.productos.domain.Producto;
import com.pos.productos.repo.ProductoRepository;
import com.pos.reportes.dto.ReporteDtos;
import com.pos.usuarios.domain.Usuario;
import com.pos.ventas.domain.DetalleVenta;
import com.pos.ventas.domain.Venta;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

@Service
public class ReportesService {

    private final EmpresaAccess empresaAccess;
    private final EmpresaRepository empresaRepository;
    private final SucursalRepository sucursalRepository;
    private final StockRepository stockRepository;
    private final ProductoRepository productoRepository;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final GeminiClient geminiClient;

    @PersistenceContext
    private EntityManager entityManager;

    public ReportesService(
            EmpresaAccess empresaAccess,
            EmpresaRepository empresaRepository,
            SucursalRepository sucursalRepository,
            StockRepository stockRepository,
            ProductoRepository productoRepository,
            MovimientoInventarioRepository movimientoInventarioRepository,
            GeminiClient geminiClient) {
        this.empresaAccess = empresaAccess;
        this.empresaRepository = empresaRepository;
        this.sucursalRepository = sucursalRepository;
        this.stockRepository = stockRepository;
        this.productoRepository = productoRepository;
        this.movimientoInventarioRepository = movimientoInventarioRepository;
        this.geminiClient = geminiClient;
    }

    public List<ReporteDtos.Plantilla> catalogo() {
        return List.of(
                plantilla("movimientos_inventario", "Movimientos de inventario",
                        "Historial de movimientos de stock por producto, tipo, sucursal y usuario.",
                        List.of("cantidad"), List.of("fecha", "producto", "tipo_movimiento", "sucursal", "usuario"),
                        "tabla", Map.of("periodo", "ultimos_30_dias"),
                        List.of("inventario", "movimientos", "historial", "kardex")),
                plantilla("resumen_ventas", "Resumen de ventas",
                        "Resumen ejecutivo de ventas por periodo y sucursal.",
                        List.of("total_ventas", "cantidad_ventas", "ticket_promedio"),
                        List.of("fecha", "sucursal"), "tabla",
                        Map.of("periodo", "ultimos_30_dias", "granularidad_fecha", "dia"),
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
                        "Top N de productos por cantidad o por ventas.",
                        List.of("cantidad_vendida", "total_ventas"), List.of("producto"), "tabla",
                        Map.of("periodo", "ultimos_30_dias", "top_n", 10),
                        List.of("top", "producto", "ventas", "cantidad")),
                plantilla("alertas_stock", "Alertas de stock",
                        "Productos con stock igual o inferior al minimo.",
                        List.of("cantidad_actual", "stock_minimo", "diferencia"),
                        List.of("producto", "sucursal"), "tabla",
                        Map.of("solo_bajo_minimo", true), List.of("stock", "inventario", "alertas")),
                plantilla("movimientos_caja", "Movimientos de caja",
                        "Entradas, salidas y neto de caja por periodo y sucursal.",
                        List.of("ingresos", "egresos", "neto"),
                        List.of("fecha", "sucursal", "tipo_movimiento"), "tabla",
                        Map.of("periodo", "ultimos_30_dias"), List.of("caja", "movimientos", "saldo")),
                plantilla("reparticion_metodos_pago", "Reparticion por metodo de pago",
                        "Distribucion de ventas por metodo de pago.",
                        List.of("total_ventas", "cantidad_transacciones"), List.of("metodo_pago"), "torta",
                        Map.of("periodo", "ultimos_30_dias"), List.of("pago", "ventas", "metodo")),
                plantilla("comparar_periodos", "Comparar periodos",
                        "Comparacion entre un periodo actual y uno anterior.",
                        List.of("total_ventas", "cantidad_ventas", "crecimiento_pct"), List.of("periodo"), "tarjetas",
                        Map.of("periodo", "ultimos_30_dias"), List.of("comparativo", "periodo", "crecimiento")),
                plantilla("ventas_detalle", "Ventas (detalle)",
                        "Listado de ventas (una fila por venta) con detalles basicos.",
                        List.of(), List.of("id_venta", "fecha", "sucursal", "cliente", "metodo_pago", "total"),
                        "tabla", Map.of("periodo", "ultimos_30_dias"), List.of("ventas", "detalle", "historial")));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resumenVentas(Usuario usuario, Integer empresaId, LocalDate fecha) {
        empresaAccess.requireEmpresa(usuario, empresaId);
        LocalDate dia = fecha == null ? LocalDate.now() : fecha;
        LocalDateTime desde = dia.atStartOfDay();
        LocalDateTime hasta = dia.atTime(LocalTime.MAX);
        List<Sucursal> sucursales = sucursalRepository.findByEmpresa_IdEmpresa(empresaId);
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
            sucursalDto.put("ventas_dia", ventasDia);
            sucursalDto.put("top_productos", topProductos);
            sucursalDtos.add(sucursalDto);
            totalVentas += cantidad;
            montoVendido += monto;
            productosVendidos += unidades;
        }
        Map<String, Object> total = new LinkedHashMap<>();
        total.put("total_ventas", totalVentas);
        total.put("monto_vendido", round(montoVendido));
        total.put("ticket_promedio", totalVentas == 0 ? 0 : round(montoVendido / totalVentas));
        total.put("productos_vendidos", productosVendidos);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id_empresa", empresaId);
        response.put("fecha", dia.toString());
        response.put("sucursales", sucursalDtos);
        response.put("total_empresa", total);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detalleVentas(Usuario usuario, Integer empresaId, LocalDate fecha) {
        empresaAccess.requireEmpresa(usuario, empresaId);
        LocalDate dia = fecha == null ? LocalDate.now() : fecha;
        LocalDateTime desde = dia.atStartOfDay();
        LocalDateTime hasta = dia.atTime(LocalTime.MAX);
        List<Map<String, Object>> sucursalDtos = new ArrayList<>();
        int totalRegistros = 0;
        double totalVendido = 0;
        for (Sucursal sucursal : sucursalRepository.findByEmpresa_IdEmpresa(empresaId)) {
            List<Venta> ventas = ventasDeSucursal(sucursal.getIdSucursal(), desde, hasta);
            List<Map<String, Object>> filas = new ArrayList<>();
            double monto = 0;
            for (Venta venta : ventas) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id_venta", venta.getIdVenta());
                row.put("numero_venta", String.valueOf(venta.getIdVenta()));
                row.put("hora", venta.getFecha() == null ? "" : venta.getFecha().toLocalTime().toString());
                row.put("cliente", venta.getIdCliente() == null ? null : "Cliente " + venta.getIdCliente());
                row.put("subtotal", venta.getSubtotal() == null ? 0 : venta.getSubtotal().doubleValue());
                row.put("descuento", venta.getDescuentoTotal() == null ? 0 : venta.getDescuentoTotal().doubleValue());
                row.put("total", venta.getTotal() == null ? 0 : venta.getTotal().doubleValue());
                filas.add(row);
                monto += venta.getTotal() == null ? 0 : venta.getTotal().doubleValue();
            }
            Map<String, Object> resumen = Map.of("total_registros", filas.size(), "total_vendido", round(monto));
            Map<String, Object> sucursalDto = new LinkedHashMap<>();
            sucursalDto.put("id_sucursal", sucursal.getIdSucursal());
            sucursalDto.put("sucursal", sucursal.getNombre());
            sucursalDto.put("ventas", filas);
            sucursalDto.put("resumen_sucursal", resumen);
            sucursalDtos.add(sucursalDto);
            totalRegistros += filas.size();
            totalVendido += monto;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id_empresa", empresaId);
        response.put("fecha", dia.toString());
        response.put("sucursales", sucursalDtos);
        response.put("total_empresa", Map.of("total_registros", totalRegistros, "total_vendido", round(totalVendido)));
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> estadoInventario(Usuario usuario, Integer empresaId) {
        empresaAccess.requireEmpresa(usuario, empresaId);
        List<Map<String, Object>> sucursalDtos = new ArrayList<>();
        int totalProductos = 0;
        int bajo = 0;
        int sobre = 0;
        int agotados = 0;
        for (Sucursal sucursal : sucursalRepository.findByEmpresa_IdEmpresa(empresaId)) {
            List<Stock> stocks = stockRepository.findByIdSucursal(sucursal.getIdSucursal());
            List<Map<String, Object>> productos = new ArrayList<>();
            int sBajo = 0;
            int sSobre = 0;
            int sAgotados = 0;
            for (Stock stock : stocks) {
                if (stock.getProducto() == null || !empresaId.equals(stock.getProducto().getIdEmpresa())) {
                    continue;
                }
                String estado = "normal";
                if (stock.getCantidad() <= 0) {
                    estado = "agotado";
                    sAgotados++;
                } else if (stock.getStockMinimo() != null && stock.getCantidad() <= stock.getStockMinimo()) {
                    estado = "bajo";
                    sBajo++;
                } else if (stock.getStockMaximo() != null && stock.getCantidad() > stock.getStockMaximo()) {
                    estado = "sobre";
                    sSobre++;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id_producto", stock.getProducto().getIdProducto());
                row.put("producto", stock.getProducto().getNombre());
                row.put("stock_actual", stock.getCantidad());
                row.put("stock_minimo", stock.getStockMinimo());
                row.put("stock_maximo", stock.getStockMaximo());
                row.put("estado", estado);
                productos.add(row);
            }
            Map<String, Object> resumen = new LinkedHashMap<>();
            resumen.put("total_productos", productos.size());
            resumen.put("productos_bajo_stock", sBajo);
            resumen.put("productos_sobre_stock", sSobre);
            resumen.put("productos_agotados", sAgotados);
            Map<String, Object> sucursalDto = new LinkedHashMap<>();
            sucursalDto.put("id_sucursal", sucursal.getIdSucursal());
            sucursalDto.put("sucursal", sucursal.getNombre());
            sucursalDto.put("productos", productos);
            sucursalDto.put("resumen_sucursal", resumen);
            sucursalDtos.add(sucursalDto);
            totalProductos += productos.size();
            bajo += sBajo;
            sobre += sSobre;
            agotados += sAgotados;
        }
        Map<String, Object> total = new LinkedHashMap<>();
        total.put("total_productos", totalProductos);
        total.put("productos_bajo_stock", bajo);
        total.put("productos_sobre_stock", sobre);
        total.put("productos_agotados", agotados);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id_empresa", empresaId);
        response.put("fecha", LocalDate.now().toString());
        response.put("sucursales", sucursalDtos);
        response.put("total_empresa", total);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> movimientosInventario(Usuario usuario, Integer empresaId) {
        empresaAccess.requireEmpresa(usuario, empresaId);
        LocalDate fin = LocalDate.now();
        LocalDate inicio = fin.minusDays(29);
        List<Map<String, Object>> sucursalDtos = new ArrayList<>();
        int totalEntradas = 0;
        int totalSalidas = 0;
        for (Sucursal sucursal : sucursalRepository.findByEmpresa_IdEmpresa(empresaId)) {
            List<MovimientoInventario> movimientos = movimientoInventarioRepository.findByIdSucursal(sucursal.getIdSucursal());
            List<Map<String, Object>> rows = new ArrayList<>();
            int entradas = 0;
            int salidas = 0;
            for (MovimientoInventario mov : movimientos) {
                String direccion = mov.getTipoMovimiento() == null ? ""
                        : (mov.getTipoMovimiento().getDireccion() == null ? "" : mov.getTipoMovimiento().getDireccion());
                if (direccion.equalsIgnoreCase("ENTRADA")) {
                    entradas += mov.getCantidad();
                } else {
                    salidas += mov.getCantidad();
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id_movimiento_inventario", mov.getIdMovimientoInventario());
                row.put("fecha", mov.getFechaMovimiento() == null ? null : mov.getFechaMovimiento().toLocalDate().toString());
                row.put("tipo_movimiento", mov.getTipoMovimiento() == null ? "" : mov.getTipoMovimiento().getNombre());
                row.put("direccion", direccion);
                row.put("producto", mov.getProducto() == null ? "" : mov.getProducto().getNombre());
                row.put("cantidad", mov.getCantidad());
                rows.add(row);
            }
            Map<String, Object> sucursalDto = new LinkedHashMap<>();
            sucursalDto.put("id_sucursal", sucursal.getIdSucursal());
            sucursalDto.put("sucursal", sucursal.getNombre());
            sucursalDto.put("movimientos", rows);
            sucursalDto.put("resumen_sucursal", Map.of("total_entradas", entradas, "total_salidas", salidas));
            sucursalDtos.add(sucursalDto);
            totalEntradas += entradas;
            totalSalidas += salidas;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id_empresa", empresaId);
        response.put("fecha_inicio", inicio.toString());
        response.put("fecha_fin", fin.toString());
        response.put("sucursales", sucursalDtos);
        response.put("total_empresa", Map.of("total_entradas", totalEntradas, "total_salidas", totalSalidas));
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resumenCajas(Usuario usuario, Integer empresaId) {
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
        response.put("fecha", LocalDate.now().toString());
        response.put("sucursales", sucursales);
        response.put("total_empresa", Map.of(
                "total_cajas", totalCajas,
                "cajas_abiertas", abiertas,
                "cajas_cerradas", cerradas,
                "ingresos", 0,
                "egresos", 0,
                "flujo_neto", 0));
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> movimientosCaja(Usuario usuario, Integer empresaId) {
        empresaAccess.requireEmpresa(usuario, empresaId);
        List<?> rows = entityManager.createQuery("""
                select s.idSucursal, s.nombre, mc.idMovimientoCaja, mc.fecha, c.nombre, t.nombre, mc.concepto, mc.monto
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
        for (Object raw : rows) {
            Object[] row = (Object[]) raw;
            Integer idSucursal = (Integer) row[0];
            Map<String, Object> sucursalDto = porSucursal.computeIfAbsent(idSucursal, id -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id_sucursal", id);
                map.put("sucursal", row[1]);
                map.put("movimientos", new ArrayList<Map<String, Object>>());
                map.put("_ing", 0d);
                map.put("_egr", 0d);
                return map;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> movimientos = (List<Map<String, Object>>) sucursalDto.get("movimientos");
            String tipo = String.valueOf(row[5]);
            double monto = row[7] == null ? 0 : ((BigDecimal) row[7]).doubleValue();
            boolean ingreso = tipo.toUpperCase(Locale.ROOT).contains("INGRESO");
            Map<String, Object> mov = new LinkedHashMap<>();
            mov.put("id_movimiento_caja", row[2]);
            mov.put("hora", row[3] == null ? "" : row[3].toString());
            mov.put("caja", row[4]);
            mov.put("tipo", tipo);
            mov.put("concepto", row[6]);
            mov.put("monto", round(monto));
            movimientos.add(mov);
            if (ingreso) {
                sucursalDto.put("_ing", (double) sucursalDto.get("_ing") + monto);
                ingresos += monto;
            } else {
                sucursalDto.put("_egr", (double) sucursalDto.get("_egr") + monto);
                egresos += monto;
            }
            totalMovs++;
        }
        List<Map<String, Object>> sucursales = new ArrayList<>();
        for (Map<String, Object> sucursalDto : porSucursal.values()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> movimientos = (List<Map<String, Object>>) sucursalDto.get("movimientos");
            double ing = (double) sucursalDto.remove("_ing");
            double egr = (double) sucursalDto.remove("_egr");
            sucursalDto.put("resumen_sucursal", Map.of(
                    "total_movimientos", movimientos.size(),
                    "total_ingresos", round(ing),
                    "total_egresos", round(egr)));
            sucursales.add(sucursalDto);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id_empresa", empresaId);
        response.put("fecha", LocalDate.now().toString());
        response.put("sucursales", sucursales);
        response.put("total_empresa", Map.of(
                "total_movimientos", totalMovs,
                "total_ingresos", round(ingresos),
                "total_egresos", round(egresos)));
        return response;
    }

    public ReporteDtos.RespuestaInterpretacion interpretar(Integer empresaId, String prompt) {
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
        } else if (texto.contains("sucursal")) {
            id = "ventas_por_sucursal";
        } else if (texto.contains("top") || texto.contains("mas vend")) {
            id = "productos_top";
        } else if (texto.contains("producto")) {
            id = "ventas_por_producto";
        } else if (texto.contains("caja")) {
            id = "movimientos_caja";
        } else if (texto.contains("metodo") || texto.contains("pago")) {
            id = "reparticion_metodos_pago";
        } else if (texto.contains("compar")) {
            id = "comparar_periodos";
        } else if (texto.contains("detalle")) {
            id = "ventas_detalle";
        }
        final String identificador = id;
        ReporteDtos.Plantilla plantilla = catalogo().stream()
                .filter(p -> p.getIdentificador().equals(identificador))
                .findFirst()
                .orElse(catalogo().get(0));
        ReporteDtos.Especificacion spec = new ReporteDtos.Especificacion();
        spec.setIdentificadorPlantilla(plantilla.getIdentificador());
        spec.setTitulo(plantilla.getNombre());
        spec.setMetricas(plantilla.getMetricas());
        spec.setDimensiones(plantilla.getDimensiones());
        spec.setFiltros(plantilla.getFiltrosPorDefecto());
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
                    if (!periodo.isBlank()) {
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
                    spec.setSolicitaAclaracion(json.path("solicita_aclaracion").asBoolean(false));
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

    @Transactional(readOnly = true)
    public ReporteDtos.RespuestaReporte ejecutar(Usuario usuario, Integer empresaId, String prompt) {
        empresaAccess.requireEmpresa(usuario, empresaId);
        ReporteDtos.RespuestaInterpretacion interpretacion = interpretar(empresaId, prompt);
        String id = interpretacion.getEspecificacion().getIdentificadorPlantilla();
        List<Map<String, Object>> filas = new ArrayList<>();
        Map<String, Object> agregados = new LinkedHashMap<>();
        if (Boolean.TRUE.equals(interpretacion.getEspecificacion().getSolicitaAclaracion())) {
            // La IA necesita mas informacion: no se ejecuta ningun reporte, se devuelve la pregunta.
        } else if ("alertas_stock".equals(id)) {
            Map<String, Object> inventario = estadoInventario(usuario, empresaId);
            agregados.put("resumen", inventario.get("total_empresa"));
            filas.add(inventario);
        } else if ("movimientos_inventario".equals(id) || id.contains("inventario")) {
            filas.add(movimientosInventario(usuario, empresaId));
        } else if ("movimientos_caja".equals(id)) {
            filas.add(movimientosCaja(usuario, empresaId));
        } else if ("ventas_detalle".equals(id)) {
            filas.add(detalleVentas(usuario, empresaId, LocalDate.now()));
        } else {
            filas.add(resumenVentas(usuario, empresaId, LocalDate.now()));
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
        ReporteDtos.Columna col = new ReporteDtos.Columna();
        col.setNombre("resultado");
        col.setEtiqueta("Resultado");
        col.setTipo("objeto");
        respuesta.setColumnas(List.of(col));
        return respuesta;
    }

    @Transactional(readOnly = true)
    public List<ReporteDtos.ProductoAbastecimiento> abastecimiento(Usuario usuario, Integer idSucursal) {
        Sucursal sucursal = sucursalRepository.findById(idSucursal)
                .orElseThrow(() -> ApiException.notFound("Sucursal no encontrada."));
        empresaAccess.requireEmpresa(usuario, sucursal.getEmpresa().getIdEmpresa());
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
            int recomendado = Math.max(0, prediccion - (stock.getCantidad() == null ? 0 : stock.getCantidad()));
            ReporteDtos.ProductoAbastecimiento dto = new ReporteDtos.ProductoAbastecimiento();
            dto.setIdProducto(producto.getIdProducto());
            dto.setProducto(producto.getNombre());
            dto.setVendidoUltimos30Dias(vendido30);
            dto.setStockActual(stock.getCantidad());
            dto.setPromedioDiario(round(promedio));
            dto.setPrediccionProximos30Dias(prediccion);
            dto.setRecomendadoComprar(recomendado);
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
        Integer idSub = origen.get(0).getSubcategoria() == null ? null : origen.get(0).getSubcategoria().getIdSubcategoria();
        List<Producto> candidatos = idSub == null
                ? productoRepository.findByIdEmpresa(idEmpresa)
                : productoRepository.findBySubcategoria_IdSubcategoria(idSub);
        List<ReporteDtos.ProductoRecomendado> recs = new ArrayList<>();
        for (Producto producto : candidatos) {
            if (ids.contains(producto.getIdProducto()) || !Boolean.TRUE.equals(producto.getActivo())) {
                continue;
            }
            int stock = stockRepository.findByProducto_IdProducto(producto.getIdProducto()).stream()
                    .mapToInt(s -> s.getCantidad() == null ? 0 : s.getCantidad())
                    .sum();
            ReporteDtos.ProductoRecomendado dto = new ReporteDtos.ProductoRecomendado();
            dto.setIdProducto(producto.getIdProducto());
            dto.setNombre(producto.getNombre());
            dto.setUnidadMedida(producto.getUnidadMedida());
            dto.setPrecio(producto.getPrecio() == null ? 0 : producto.getPrecio().doubleValue());
            dto.setStock(stock);
            dto.setCodigo(String.valueOf(producto.getIdProducto()));
            dto.setCodigoBarras(producto.getCodigoBarra());
            recs.add(dto);
            if (recs.size() >= 8) {
                break;
            }
        }
        ReporteDtos.RecomendacionResponse response = new ReporteDtos.RecomendacionResponse();
        response.setProductosAnalizados(ids);
        response.setRecomendaciones(recs);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ventasParametrizado(Usuario usuario, Integer empresaId, ReporteDtos.VentasParamRequest filtros) {
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
            jpql += " and s.idSucursal = :idSucursal";
        }
        if (filtros.getIdTipoVenta() != null) {
            jpql += " and v.tipoVenta.idTipoVenta = :idTipoVenta";
        }
        if (filtros.getIdUsuario() != null) {
            jpql += " and v.idUsuario = :idUsuario";
        }
        TypedQuery<Venta> query = entityManager.createQuery(jpql, Venta.class)
                .setParameter("idEmpresa", empresaId)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta);
        if (filtros.getIdSucursal() != null) {
            query.setParameter("idSucursal", filtros.getIdSucursal());
        }
        if (filtros.getIdTipoVenta() != null) {
            query.setParameter("idTipoVenta", filtros.getIdTipoVenta());
        }
        if (filtros.getIdUsuario() != null) {
            query.setParameter("idUsuario", filtros.getIdUsuario());
        }
        List<Venta> ventas = query.getResultList();
        List<Map<String, Object>> detalle = new ArrayList<>();
        double total = 0;
        for (Venta venta : ventas) {
            if (filtros.getIdProducto() != null) {
                boolean contiene = detalles(venta.getIdVenta()).stream()
                        .anyMatch(d -> filtros.getIdProducto().equals(d.getIdProducto()));
                if (!contiene) {
                    continue;
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id_venta", venta.getIdVenta());
            row.put("numero_venta", String.valueOf(venta.getIdVenta()));
            row.put("fecha_hora", venta.getFecha() == null ? "" : venta.getFecha().toString());
            row.put("personal", venta.getIdUsuario());
            row.put("tipo", venta.getTipoVenta() == null ? null : venta.getTipoVenta().getNombre());
            row.put("metodo_pago", null);
            row.put("productos", detalles(venta.getIdVenta()).size());
            row.put("total", venta.getTotal() == null ? 0 : venta.getTotal().doubleValue());
            detalle.add(row);
            total += venta.getTotal() == null ? 0 : venta.getTotal().doubleValue();
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id_empresa", empresaId);
        response.put("empresa", empresa.getNombre());
        response.put("fecha_generacion", LocalDateTime.now().toString());
        response.put("filtros_aplicados", Map.of(
                "periodo", desde.toLocalDate() + " / " + hasta.toLocalDate(),
                "sucursal", filtros.getIdSucursal() == null ? "todas" : String.valueOf(filtros.getIdSucursal()),
                "tipo_venta", filtros.getIdTipoVenta() == null ? "todos" : String.valueOf(filtros.getIdTipoVenta()),
                "metodo_pago", filtros.getIdMetodoPago() == null ? "todos" : String.valueOf(filtros.getIdMetodoPago()),
                "producto", filtros.getIdProducto() == null ? "todos" : String.valueOf(filtros.getIdProducto()),
                "personal", filtros.getIdUsuario() == null ? "todos" : String.valueOf(filtros.getIdUsuario())));
        response.put("resumen_gerencial", List.of(
                Map.of("indicador", "cantidad_ventas", "valor", detalle.size()),
                Map.of("indicador", "monto_total", "valor", round(total))));
        response.put("detalle_analitico", detalle);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> inventarioParametrizado(Usuario usuario, Integer empresaId, ReporteDtos.InventarioParamRequest filtros) {
        empresaAccess.requireEmpresa(usuario, empresaId);
        Empresa empresa = empresaRepository.findById(empresaId).orElseThrow();
        Map<String, Object> inventario = movimientosInventario(usuario, empresaId);
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
        response.put("resumen_gerencial", List.of(Map.of("indicador", "movimientos", "valor", ((Map<?, ?>) inventario.get("total_empresa")))));
        response.put("detalle_analitico", inventario.get("sucursales"));
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> cajasParametrizado(Usuario usuario, Integer empresaId, ReporteDtos.CajasParamRequest filtros) {
        empresaAccess.requireEmpresa(usuario, empresaId);
        Empresa empresa = empresaRepository.findById(empresaId).orElseThrow();
        Map<String, Object> data = movimientosCaja(usuario, empresaId);
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
        response.put("resumen_gerencial", List.of(Map.of("indicador", "movimientos", "valor", data.get("total_empresa"))));
        response.put("detalle_analitico", data.get("sucursales"));
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

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
