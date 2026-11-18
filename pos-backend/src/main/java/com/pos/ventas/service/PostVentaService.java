package com.pos.ventas.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.clientes.domain.Cliente;
import com.pos.clientes.repo.ClienteRepository;
import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;
import com.pos.empresas.domain.CajaSesion;
import com.pos.empresas.domain.MovimientoCaja;
import com.pos.empresas.domain.TipoMovimientoCaja;
import com.pos.empresas.repo.CajaSesionRepository;
import com.pos.empresas.repo.MovimientoCajaRepository;
import com.pos.empresas.repo.TipoMovimientoCajaRepository;
import com.pos.inventario.service.InventarioService;
import com.pos.siat.service.SiatFacturaService;
import com.pos.usuarios.domain.Usuario;
import com.pos.ventas.domain.CuentaPorCobrar;
import com.pos.ventas.domain.DetalleVenta;
import com.pos.ventas.domain.Devolucion;
import com.pos.ventas.domain.DevolucionDetalle;
import com.pos.ventas.domain.Venta;
import com.pos.ventas.domain.VentaPago;
import com.pos.ventas.repo.CuentaPorCobrarRepository;
import com.pos.ventas.repo.DetalleVentaRepository;
import com.pos.ventas.repo.DevolucionDetalleRepository;
import com.pos.ventas.repo.DevolucionRepository;
import com.pos.ventas.repo.FacturaRepository;
import com.pos.ventas.repo.MetodoPagoRepository;
import com.pos.ventas.repo.PagoCreditoRepository;
import com.pos.ventas.repo.VentaPagoRepository;
import com.pos.ventas.repo.VentaRepository;

/**
 * Operaciones posteriores a la venta: anulacion (HU-14) y devolucion de productos (HU-15).
 * Ambas reponen el stock, devuelven el dinero por caja o como saldo a favor y quedan en la bitacora
 * (las registra el interceptor de auditoria al ser peticiones POST).
 */
@Service
public class PostVentaService {

    public static final String ANULADA = "ANULADA";
    public static final String DEVUELTA = "DEVUELTA";
    public static final String EFECTIVO = "EFECTIVO";
    public static final String SALDO_A_FAVOR = "SALDO_A_FAVOR";
    /** Plazo maximo para aceptar devoluciones, contado desde la fecha de la venta. */
    public static final int PLAZO_DEVOLUCION_DIAS = 7;
    /** Motivo SIN usado al anular la factura de una venta anulada: 1 = FACTURA MAL EMITIDA. */
    private static final int MOTIVO_SIN_POR_DEFECTO = 1;

    private final EmpresaAccess empresaAccess;
    private final VentaRepository ventas;
    private final DetalleVentaRepository detalles;
    private final VentaPagoRepository pagos;
    private final CuentaPorCobrarRepository cuentas;
    private final PagoCreditoRepository pagosCredito;
    private final ClienteRepository clientes;
    private final CajaSesionRepository sesiones;
    private final MovimientoCajaRepository movimientosCaja;
    private final TipoMovimientoCajaRepository tiposCaja;
    private final MetodoPagoRepository metodos;
    private final InventarioService inventario;
    private final FacturaRepository facturas;
    private final SiatFacturaService siat;
    private final DevolucionRepository devoluciones;
    private final DevolucionDetalleRepository devolucionDetalles;

    public PostVentaService(EmpresaAccess empresaAccess, VentaRepository ventas, DetalleVentaRepository detalles,
            VentaPagoRepository pagos, CuentaPorCobrarRepository cuentas, PagoCreditoRepository pagosCredito,
            ClienteRepository clientes, CajaSesionRepository sesiones, MovimientoCajaRepository movimientosCaja,
            TipoMovimientoCajaRepository tiposCaja, MetodoPagoRepository metodos, InventarioService inventario,
            FacturaRepository facturas, SiatFacturaService siat, DevolucionRepository devoluciones,
            DevolucionDetalleRepository devolucionDetalles) {
        this.empresaAccess = empresaAccess;
        this.ventas = ventas;
        this.detalles = detalles;
        this.pagos = pagos;
        this.cuentas = cuentas;
        this.pagosCredito = pagosCredito;
        this.clientes = clientes;
        this.sesiones = sesiones;
        this.movimientosCaja = movimientosCaja;
        this.tiposCaja = tiposCaja;
        this.metodos = metodos;
        this.inventario = inventario;
        this.facturas = facturas;
        this.siat = siat;
        this.devoluciones = devoluciones;
        this.devolucionDetalles = devolucionDetalles;
    }

    public record LineaDevolucion(Integer idProducto, Integer cantidad) {}

    // ---------------------------------------------------------------------------------------------
    // HU-14 Anulacion
    // ---------------------------------------------------------------------------------------------

    /**
     * Anula una venta completa: repone todo el stock, devuelve el dinero en la misma caja (que debe
     * seguir abierta), cancela la cuenta por cobrar si era a credito y anula la factura en el SIN.
     */
    @Transactional
    public Map<String, Object> anular(Usuario usuario, Integer idVenta, String motivo, Integer codigoMotivoSin) {
        Venta venta = ventas.findById(idVenta).orElseThrow(() -> ApiException.notFound("Venta no encontrada."));
        CajaSesion sesion = sesionDe(venta);
        Integer idEmpresa = sesion.getCaja().getSucursal().getEmpresa().getIdEmpresa();
        Integer idSucursal = sesion.getCaja().getSucursal().getIdSucursal();
        empresaAccess.requirePermiso(usuario, idEmpresa, idSucursal, "sale:void");
        String motivoLimpio = requireMotivo(motivo);
        if (ANULADA.equalsIgnoreCase(venta.getEstado())) throw ApiException.badRequest("La venta ya está anulada.");
        if (!devoluciones.findByVenta_IdVentaOrderByFechaAsc(idVenta).isEmpty())
            throw ApiException.badRequest("La venta tiene devoluciones registradas; no se puede anular.");
        if (!"Abierto".equalsIgnoreCase(sesion.getEstado()))
            throw ApiException.badRequest("La caja de esta venta ya se cerró. Para devolver productos registra una devolución.");

        CuentaPorCobrar cuenta = cuentas.findByVenta_IdVenta(idVenta).stream().findFirst().orElse(null);
        if (cuenta != null && !pagosCredito.findByCuentaPorCobrar_IdCxc(cuenta.getIdCxc()).isEmpty())
            throw ApiException.badRequest("La venta a crédito ya tiene pagos registrados; registra una devolución.");

        // 1) Stock: reingresan todas las unidades vendidas.
        for (DetalleVenta d : detalles.findByVenta_IdVenta(idVenta)) {
            inventario.registrarEntradaPostVenta(usuario, idEmpresa, idSucursal, d.getIdProducto(), d.getCantidad(),
                    "Anulacion de venta", "Anulación de la venta " + idVenta + ": " + motivoLimpio);
        }
        // 2) Dinero: egreso por cada pago en efectivo/QR/tarjeta; el saldo a favor vuelve al cliente.
        TipoMovimientoCaja egreso = tipoCaja("EGRESO");
        for (VentaPago pago : pagos.findByIdVenta(idVenta)) {
            if (esSaldoAFavor(pago.getIdMetodoPago())) {
                devolverSaldoAFavor(venta.getIdCliente(), pago.getMonto());
                continue;
            }
            registrarMovimientoCaja(sesion, egreso, pago.getIdMetodoPago(), pago.getMonto(), usuario,
                    "Anulación venta " + idVenta);
        }
        // 3) Credito: la deuda desaparece.
        if (cuenta != null) {
            cuenta.setSaldoPendiente(BigDecimal.ZERO);
            cuenta.setEstado(ANULADA);
            cuentas.save(cuenta);
            recalcularSaldoCliente(venta.getIdCliente());
        }
        // 4) Factura: se anula tambien ante el SIN.
        String mensajeFactura = null;
        var factura = facturas.findByVenta_IdVenta(idVenta).orElse(null);
        if (factura != null && !SiatFacturaService.ANULADA.equals(factura.getEstadoSiat())) {
            var r = siat.anular(usuario, factura.getIdFactura(), codigoMotivoSin == null ? MOTIVO_SIN_POR_DEFECTO : codigoMotivoSin);
            mensajeFactura = String.valueOf(r.get("mensaje"));
        }
        venta.setEstado(ANULADA);
        venta.setMotivoAnulacion(motivoLimpio);
        venta.setFechaAnulacion(LocalDateTime.now());
        venta.setIdUsuarioAnulacion(usuario.getIdUsuario());
        ventas.save(venta);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id_venta", idVenta);
        result.put("estado", ANULADA);
        result.put("monto_devuelto", venta.getTotal());
        result.put("mensaje", "Venta " + idVenta + " anulada. Se repuso el stock y se registró la salida de dinero en caja."
                + (mensajeFactura == null ? "" : " " + mensajeFactura));
        return result;
    }

    // ---------------------------------------------------------------------------------------------
    // HU-15 Devolucion
    // ---------------------------------------------------------------------------------------------

    /**
     * Devolucion parcial o total dentro del plazo. El reembolso sale en efectivo de la caja abierta del
     * usuario (en la misma sucursal) o se abona como saldo a favor: primero cancela deudas del cliente
     * y lo que sobra queda para su proxima compra.
     */
    @Transactional
    public Map<String, Object> devolver(Usuario usuario, Integer idVenta, List<LineaDevolucion> lineas, String tipoReembolso,
            String motivo) {
        Venta venta = ventas.findById(idVenta).orElseThrow(() -> ApiException.notFound("Venta no encontrada."));
        CajaSesion sesionVenta = sesionDe(venta);
        Integer idEmpresa = sesionVenta.getCaja().getSucursal().getEmpresa().getIdEmpresa();
        Integer idSucursal = sesionVenta.getCaja().getSucursal().getIdSucursal();
        empresaAccess.requirePermiso(usuario, idEmpresa, idSucursal, "sale:void");
        String motivoLimpio = requireMotivo(motivo);
        String tipo = tipoReembolso == null ? "" : tipoReembolso.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(EFECTIVO, SALDO_A_FAVOR).contains(tipo))
            throw ApiException.badRequest("Elige el tipo de reembolso: efectivo o saldo a favor.");
        if (Set.of(ANULADA, DEVUELTA).contains(String.valueOf(venta.getEstado()).toUpperCase(Locale.ROOT)))
            throw ApiException.badRequest("La venta está " + venta.getEstado().toLowerCase(Locale.ROOT) + "; no admite devoluciones.");
        if (venta.getFecha().plusDays(PLAZO_DEVOLUCION_DIAS).isBefore(LocalDateTime.now()))
            throw ApiException.badRequest("El plazo de devolución es de " + PLAZO_DEVOLUCION_DIAS + " días y ya venció para esta venta.");
        if (lineas == null || lineas.isEmpty()) throw ApiException.badRequest("Indica los productos y cantidades a devolver.");
        if (SALDO_A_FAVOR.equals(tipo) && venta.getIdCliente() == null)
            throw ApiException.badRequest("El saldo a favor requiere que la venta tenga un cliente.");
        CuentaPorCobrar cuentaVenta = cuentas.findByVenta_IdVenta(idVenta).stream().findFirst().orElse(null);
        if (EFECTIVO.equals(tipo) && cuentaVenta != null)
            throw ApiException.badRequest("En una venta a crédito el reembolso se aplica como saldo a favor del cliente.");

        // Cantidades vendidas, ya devueltas y precio neto unitario por producto.
        Map<Integer, Integer> vendido = new LinkedHashMap<>();
        Map<Integer, BigDecimal> totalLinea = new LinkedHashMap<>();
        for (DetalleVenta d : detalles.findByVenta_IdVenta(idVenta)) {
            vendido.merge(d.getIdProducto(), d.getCantidad(), Integer::sum);
            totalLinea.merge(d.getIdProducto(), d.getTotal(), BigDecimal::add);
        }
        Map<Integer, Integer> yaDevuelto = new LinkedHashMap<>();
        for (Object[] row : devolucionDetalles.devueltoPorProducto(idVenta))
            yaDevuelto.put(((Number) row[0]).intValue(), ((Number) row[1]).intValue());

        Map<Integer, Integer> pedido = new LinkedHashMap<>();
        for (LineaDevolucion l : lineas) {
            if (l == null || l.idProducto() == null || l.cantidad() == null || l.cantidad() <= 0)
                throw ApiException.badRequest("Cada producto a devolver necesita una cantidad mayor a cero.");
            pedido.merge(l.idProducto(), l.cantidad(), Integer::sum);
        }
        for (var e : pedido.entrySet()) {
            int disponible = vendido.getOrDefault(e.getKey(), 0) - yaDevuelto.getOrDefault(e.getKey(), 0);
            if (!vendido.containsKey(e.getKey())) throw ApiException.badRequest("El producto " + e.getKey() + " no forma parte de la venta.");
            if (e.getValue() > disponible)
                throw ApiException.badRequest("Solo quedan " + disponible + " unidades por devolver del producto " + e.getKey() + ".");
        }

        CajaSesion sesionReembolso = null;
        if (EFECTIVO.equals(tipo)) {
            sesionReembolso = sesiones.findAbiertasPorUsuario(usuario.getIdUsuario(), "Abierto").stream()
                    .filter(s -> s.getCaja().getSucursal().getIdSucursal().equals(idSucursal)).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Abre tu caja en esta sucursal para devolver dinero en efectivo."));
        }

        Devolucion devolucion = new Devolucion();
        devolucion.setVenta(venta);
        devolucion.setIdUsuario(usuario.getIdUsuario());
        devolucion.setIdCajaSesion(sesionReembolso == null ? null : sesionReembolso.getIdCajaSesion());
        devolucion.setFecha(LocalDateTime.now());
        devolucion.setMotivo(motivoLimpio);
        devolucion.setTipoReembolso(tipo);
        devolucion = devoluciones.save(devolucion);

        BigDecimal monto = BigDecimal.ZERO;
        List<Map<String, Object>> items = new ArrayList<>();
        for (var e : pedido.entrySet()) {
            int idProducto = e.getKey();
            int cantidad = e.getValue();
            BigDecimal unitario = totalLinea.get(idProducto)
                    .divide(BigDecimal.valueOf(vendido.get(idProducto)), 2, RoundingMode.HALF_UP);
            // La ultima unidad pendiente devuelve el resto exacto para no dejar centavos por redondeo.
            boolean ultimas = cantidad == vendido.get(idProducto) - yaDevuelto.getOrDefault(idProducto, 0);
            BigDecimal yaReembolsado = unitario.multiply(BigDecimal.valueOf(yaDevuelto.getOrDefault(idProducto, 0)));
            BigDecimal subtotal = ultimas ? totalLinea.get(idProducto).subtract(yaReembolsado)
                    : unitario.multiply(BigDecimal.valueOf(cantidad));
            DevolucionDetalle detalle = new DevolucionDetalle();
            detalle.setDevolucion(devolucion);
            detalle.setIdProducto(idProducto);
            detalle.setCantidad(cantidad);
            detalle.setPrecioUnitario(unitario);
            detalle.setSubtotal(subtotal);
            devolucionDetalles.save(detalle);
            monto = monto.add(subtotal);
            inventario.registrarEntradaPostVenta(usuario, idEmpresa, idSucursal, idProducto, cantidad,
                    "Devolucion de venta", "Devolución de la venta " + idVenta + ": " + motivoLimpio);
            items.add(Map.of("id_producto", idProducto, "cantidad", cantidad, "subtotal", subtotal));
        }
        devolucion.setMonto(monto);

        String detalleReembolso;
        if (EFECTIVO.equals(tipo)) {
            Integer efectivo = metodos.findByNombreIgnoreCase("EFECTIVO")
                    .orElseThrow(() -> ApiException.badRequest("Método de pago EFECTIVO no configurado.")).getIdMetodoPago();
            registrarMovimientoCaja(sesionReembolso, tipoCaja("EGRESO"), efectivo, monto, usuario,
                    "Devolución venta " + idVenta);
            detalleReembolso = "Se devolvieron Bs " + monto + " en efectivo desde tu caja.";
        } else {
            BigDecimal aFavor = aplicarSaldoAFavor(venta.getIdCliente(), monto);
            detalleReembolso = aFavor.signum() > 0
                    ? "Se abonaron Bs " + monto + " al cliente; queda un saldo a favor de Bs " + aFavor + "."
                    : "Se descontaron Bs " + monto + " de la deuda del cliente.";
        }

        // Nota de Credito-Debito simulada si la venta tenia factura (documento sector 24 en el SIN).
        var factura = facturas.findByVenta_IdVenta(idVenta).orElse(null);
        if (factura != null) devolucion.setCodigoNotaCredito(codigoNota(factura.getCuf(), devolucion.getIdDevolucion()));
        devoluciones.save(devolucion);

        boolean total = vendido.entrySet().stream().allMatch(e ->
                        yaDevuelto.getOrDefault(e.getKey(), 0) + pedido.getOrDefault(e.getKey(), 0) >= e.getValue());
        if (total) {
            venta.setEstado(DEVUELTA);
            ventas.save(venta);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id_devolucion", devolucion.getIdDevolucion());
        result.put("id_venta", idVenta);
        result.put("tipo", total ? "TOTAL" : "PARCIAL");
        result.put("tipo_reembolso", tipo);
        result.put("monto", monto);
        result.put("codigo_nota_credito", devolucion.getCodigoNotaCredito());
        result.put("items", items);
        result.put("mensaje", "Devolución " + (total ? "total" : "parcial") + " registrada. " + detalleReembolso);
        return result;
    }

    /** Devoluciones registradas de una venta, con lo que aun se puede devolver por producto. */
    @Transactional(readOnly = true)
    public Map<String, Object> resumenDevoluciones(Usuario usuario, Integer idVenta) {
        Venta venta = ventas.findById(idVenta).orElseThrow(() -> ApiException.notFound("Venta no encontrada."));
        CajaSesion sesion = sesionDe(venta);
        empresaAccess.requirePermiso(usuario, sesion.getCaja().getSucursal().getEmpresa().getIdEmpresa(),
                sesion.getCaja().getSucursal().getIdSucursal(), "sale:read");
        Map<Integer, Integer> yaDevuelto = new LinkedHashMap<>();
        for (Object[] row : devolucionDetalles.devueltoPorProducto(idVenta))
            yaDevuelto.put(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
        List<Map<String, Object>> disponibles = new ArrayList<>();
        for (DetalleVenta d : detalles.findByVenta_IdVenta(idVenta)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id_producto", d.getIdProducto());
            row.put("descripcion", d.getDescripcion());
            row.put("cantidad_vendida", d.getCantidad());
            row.put("cantidad_disponible", Math.max(0, d.getCantidad() - yaDevuelto.getOrDefault(d.getIdProducto(), 0)));
            row.put("precio_unitario_neto", d.getTotal().divide(BigDecimal.valueOf(d.getCantidad()), 2, RoundingMode.HALF_UP));
            disponibles.add(row);
        }
        List<Map<String, Object>> historial = new ArrayList<>();
        for (Devolucion dev : devoluciones.findByVenta_IdVentaOrderByFechaAsc(idVenta)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id_devolucion", dev.getIdDevolucion());
            row.put("fecha", dev.getFecha());
            row.put("motivo", dev.getMotivo());
            row.put("tipo_reembolso", dev.getTipoReembolso());
            row.put("monto", dev.getMonto());
            row.put("codigo_nota_credito", dev.getCodigoNotaCredito());
            historial.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id_venta", idVenta);
        result.put("estado", venta.getEstado());
        result.put("plazo_dias", PLAZO_DEVOLUCION_DIAS);
        result.put("vence", venta.getFecha().plusDays(PLAZO_DEVOLUCION_DIAS));
        result.put("productos", disponibles);
        result.put("devoluciones", historial);
        return result;
    }

    // ---------------------------------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------------------------------

    private CajaSesion sesionDe(Venta venta) {
        return sesiones.findWithCajaSucursalEmpresa(venta.getIdCajaSesion())
                .orElseThrow(() -> ApiException.notFound("Sesión de caja de la venta no encontrada."));
    }

    private static String requireMotivo(String motivo) {
        String limpio = motivo == null ? "" : motivo.strip();
        if (limpio.length() < 5) throw ApiException.badRequest("Escribe el motivo (al menos 5 caracteres).");
        if (limpio.length() > 255) throw ApiException.badRequest("El motivo no puede superar 255 caracteres.");
        return limpio;
    }

    private TipoMovimientoCaja tipoCaja(String nombre) {
        return tiposCaja.findByNombreIgnoreCase(nombre)
                .orElseThrow(() -> ApiException.badRequest("Tipo de movimiento de caja " + nombre + " no configurado."));
    }

    private void registrarMovimientoCaja(CajaSesion sesion, TipoMovimientoCaja tipo, Integer idMetodoPago, BigDecimal monto,
            Usuario usuario, String concepto) {
        if (monto == null || monto.signum() <= 0) return;
        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setCajaSesion(sesion);
        movimiento.setTipoMovimientoCaja(tipo);
        movimiento.setIdMetodoPago(idMetodoPago);
        movimiento.setIdUsuario(usuario.getIdUsuario());
        movimiento.setFecha(LocalDateTime.now());
        movimiento.setMonto(monto);
        movimiento.setConcepto(concepto);
        movimientosCaja.save(movimiento);
    }

    private boolean esSaldoAFavor(Integer idMetodoPago) {
        return metodos.findById(idMetodoPago).map(m -> "SALDO A FAVOR".equalsIgnoreCase(m.getNombre())).orElse(false);
    }

    private void devolverSaldoAFavor(Integer idCliente, BigDecimal monto) {
        if (idCliente == null || monto == null) return;
        Cliente cliente = clientes.lockById(idCliente).orElseThrow(() -> ApiException.notFound("Cliente no encontrado."));
        cliente.setSaldoAFavor(nvl(cliente.getSaldoAFavor()).add(monto));
        clientes.save(cliente);
    }

    /** Descuenta el monto de las deudas pendientes (la mas antigua primero) y devuelve lo que queda a favor. */
    private BigDecimal aplicarSaldoAFavor(Integer idCliente, BigDecimal monto) {
        Cliente cliente = clientes.lockById(idCliente).orElseThrow(() -> ApiException.notFound("Cliente no encontrado."));
        BigDecimal restante = monto;
        List<CuentaPorCobrar> pendientes = cuentas.findByVenta_IdCliente(idCliente).stream()
                .filter(c -> nvl(c.getSaldoPendiente()).signum() > 0 && !ANULADA.equals(c.getEstado()))
                .sorted((a, b) -> a.getFechaInicio().compareTo(b.getFechaInicio())).toList();
        for (CuentaPorCobrar c : pendientes) {
            if (restante.signum() <= 0) break;
            BigDecimal abono = restante.min(c.getSaldoPendiente());
            c.setSaldoPendiente(c.getSaldoPendiente().subtract(abono));
            if (c.getSaldoPendiente().signum() == 0) c.setEstado("PAGADA");
            cuentas.save(c);
            restante = restante.subtract(abono);
        }
        cliente.setSaldoCredito(cuentas.sumSaldoPendienteByCliente(idCliente));
        cliente.setSaldoAFavor(nvl(cliente.getSaldoAFavor()).add(restante));
        clientes.save(cliente);
        return cliente.getSaldoAFavor();
    }

    private void recalcularSaldoCliente(Integer idCliente) {
        if (idCliente == null) return;
        clientes.findById(idCliente).ifPresent(c -> {
            c.setSaldoCredito(cuentas.sumSaldoPendienteByCliente(idCliente));
            clientes.save(c);
        });
    }

    /** Codigo de control de la Nota de Credito-Debito simulada, derivado del CUF de la factura original. */
    private static String codigoNota(String cuf, Integer idDevolucion) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((cuf + "|NCD|" + idDevolucion).getBytes(StandardCharsets.UTF_8));
            return "NCD-" + HexFormat.of().withUpperCase().formatHex(hash).substring(0, 24);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
