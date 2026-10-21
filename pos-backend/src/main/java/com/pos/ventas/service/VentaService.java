package com.pos.ventas.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
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
import com.pos.inventario.domain.TipoMovimiento;
import com.pos.inventario.dto.InventarioDtos;
import com.pos.inventario.repo.TipoMovimientoRepository;
import com.pos.inventario.service.InventarioService;
import com.pos.notifications.service.NotificationService;
import com.pos.productos.domain.Producto;
import com.pos.productos.repo.ProductoRepository;
import com.pos.usuarios.domain.Usuario;
import com.pos.usuarios.repo.UsuarioRolRepository;
import com.pos.ventas.domain.CuentaPorCobrar;
import com.pos.ventas.domain.DetalleVenta;
import com.pos.ventas.domain.MetodoPago;
import com.pos.ventas.domain.PagoCredito;
import com.pos.ventas.domain.PedidoCliente;
import com.pos.ventas.domain.TipoVenta;
import com.pos.ventas.domain.Venta;
import com.pos.ventas.domain.VentaPago;
import com.pos.ventas.dto.VentaDtos;
import com.pos.ventas.repo.CuentaPorCobrarRepository;
import com.pos.ventas.repo.DetalleVentaRepository;
import com.pos.ventas.repo.MetodoPagoRepository;
import com.pos.ventas.repo.PagoCreditoRepository;
import com.pos.ventas.repo.PedidoClienteRepository;
import com.pos.ventas.repo.TipoVentaRepository;
import com.pos.ventas.repo.VentaPagoRepository;
import com.pos.ventas.repo.VentaRepository;

@Service
public class VentaService {

    private final EmpresaAccess empresaAccess;
    private final TipoVentaRepository tipoVentaRepository;
    private final MetodoPagoRepository metodoPagoRepository;
    private final CajaSesionRepository cajaSesionRepository;
    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final VentaPagoRepository ventaPagoRepository;
    private final CuentaPorCobrarRepository cuentaPorCobrarRepository;
    private final PagoCreditoRepository pagoCreditoRepository;
    private final PedidoClienteRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final TipoMovimientoRepository tipoMovimientoRepository;
    private final TipoMovimientoCajaRepository tipoMovimientoCajaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final InventarioService inventarioService;
    private final NotificationService notificationService;
    private final UsuarioRolRepository usuarioRolRepository;

    public VentaService(
            EmpresaAccess empresaAccess,
            TipoVentaRepository tipoVentaRepository,
            MetodoPagoRepository metodoPagoRepository,
            CajaSesionRepository cajaSesionRepository,
            VentaRepository ventaRepository,
            DetalleVentaRepository detalleVentaRepository,
            VentaPagoRepository ventaPagoRepository,
            CuentaPorCobrarRepository cuentaPorCobrarRepository,
            PagoCreditoRepository pagoCreditoRepository,
            PedidoClienteRepository pedidoRepository,
            ClienteRepository clienteRepository,
            ProductoRepository productoRepository,
            TipoMovimientoRepository tipoMovimientoRepository,
            TipoMovimientoCajaRepository tipoMovimientoCajaRepository,
            MovimientoCajaRepository movimientoCajaRepository,
            InventarioService inventarioService,
            NotificationService notificationService,
            UsuarioRolRepository usuarioRolRepository) {
        this.empresaAccess = empresaAccess;
        this.tipoVentaRepository = tipoVentaRepository;
        this.metodoPagoRepository = metodoPagoRepository;
        this.cajaSesionRepository = cajaSesionRepository;
        this.ventaRepository = ventaRepository;
        this.detalleVentaRepository = detalleVentaRepository;
        this.ventaPagoRepository = ventaPagoRepository;
        this.cuentaPorCobrarRepository = cuentaPorCobrarRepository;
        this.pagoCreditoRepository = pagoCreditoRepository;
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.productoRepository = productoRepository;
        this.tipoMovimientoRepository = tipoMovimientoRepository;
        this.tipoMovimientoCajaRepository = tipoMovimientoCajaRepository;
        this.movimientoCajaRepository = movimientoCajaRepository;
        this.inventarioService = inventarioService;
        this.notificationService = notificationService;
        this.usuarioRolRepository = usuarioRolRepository;
    }

    @Transactional(readOnly = true)
    public List<VentaDtos.CatalogoItem> listarTiposVenta() {
        List<VentaDtos.CatalogoItem> items = new ArrayList<>();
        for (TipoVenta tipo : tipoVentaRepository.findAll()) {
            VentaDtos.CatalogoItem item = new VentaDtos.CatalogoItem();
            item.setIdTipoVenta(tipo.getIdTipoVenta());
            item.setNombre(tipo.getNombre());
            item.setDescripcion(tipo.getDescripcion());
            items.add(item);
        }
        return items;
    }

    @Transactional(readOnly = true)
    public List<VentaDtos.CatalogoItem> listarMetodosPago() {
        List<VentaDtos.CatalogoItem> items = new ArrayList<>();
        for (MetodoPago metodo : metodoPagoRepository.findAll()) {
            VentaDtos.CatalogoItem item = new VentaDtos.CatalogoItem();
            item.setIdMetodoPago(metodo.getIdMetodoPago());
            item.setNombre(metodo.getNombre());
            item.setDescripcion(metodo.getDescripcion());
            items.add(item);
        }
        return items;
    }

    @Transactional
    public VentaDtos.VentaResponse crearVenta(Usuario usuario, Integer idCajaSesion, VentaDtos.VentaCreate payload) {
        CajaSesion sesion = requireSesionAbierta(usuario, idCajaSesion);
        Integer idEmpresa = sesion.getCaja().getSucursal().getEmpresa().getIdEmpresa();
        Integer idSucursal = sesion.getCaja().getSucursal().getIdSucursal();

        TipoVenta tipoVenta = tipoVentaRepository.findById(payload.getIdTipoVenta())
                .orElseThrow(() -> ApiException.notFound("Tipo de venta no encontrado."));
        String nombreTipo = (tipoVenta.getNombre() == null ? "" : tipoVenta.getNombre()).trim().toUpperCase(Locale.ROOT);
        boolean esCredito = nombreTipo.equals("CREDITO") || nombreTipo.equals("CRÉDITO");

        List<VentaDtos.PagoCreate> pagosPayload = payload.getPagos() == null ? new ArrayList<>() : new ArrayList<>(payload.getPagos());
        if (pagosPayload.isEmpty() && payload.getIdMetodoPago() != null) {
            VentaDtos.PagoCreate unico = new VentaDtos.PagoCreate();
            unico.setIdMetodoPago(payload.getIdMetodoPago());
            unico.setMonto(payload.getTotal());
            pagosPayload.add(unico);
        }
        if (esCredito && !pagosPayload.isEmpty()) {
            throw ApiException.badRequest("Una venta a credito no debe enviar pagos ni metodo de pago.");
        }

        Set<Integer> idsMetodos = new HashSet<>();
        BigDecimal totalPagado = BigDecimal.ZERO;
        for (VentaDtos.PagoCreate pago : pagosPayload) {
            if (pago.getIdMetodoPago() == null || pago.getIdMetodoPago() <= 0) {
                throw ApiException.badRequest("Metodo de pago invalido.");
            }
            if (!idsMetodos.add(pago.getIdMetodoPago())) {
                throw ApiException.badRequest("No se puede repetir el mismo metodo de pago en una venta.");
            }
            if (pago.getMonto() == null || pago.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest("El monto de cada pago debe ser mayor a cero.");
            }
            metodoPagoRepository.findById(pago.getIdMetodoPago())
                    .orElseThrow(() -> ApiException.notFound("Metodo de pago " + pago.getIdMetodoPago() + " no encontrado."));
            totalPagado = totalPagado.add(pago.getMonto());
        }
        if (!pagosPayload.isEmpty() && payload.getTotal() != null && totalPagado.compareTo(payload.getTotal()) != 0) {
            throw ApiException.badRequest("La suma de los pagos debe ser igual al total de la venta.");
        }

        Cliente cliente = null;
        if (payload.getIdCliente() != null) {
            if (payload.getIdCliente() <= 0) {
                throw ApiException.badRequest("Cliente invalido. Envie null si la venta no tiene cliente.");
            }
            cliente = clienteRepository.findById(payload.getIdCliente())
                    .orElseThrow(() -> ApiException.notFound("Cliente no encontrado."));
        }
        if (esCredito) {
            if (cliente == null) {
                throw ApiException.badRequest("Una venta a credito debe tener cliente.");
            }
            if (cliente.getCategoriaCliente() == null) {
                throw ApiException.badRequest("El cliente no tiene categoria asociada para calcular el plazo de credito.");
            }
        }

        PedidoCliente pedido = null;
        if (payload.getIdPedido() != null) {
            pedido = pedidoRepository.findById(payload.getIdPedido())
                    .orElseThrow(() -> ApiException.notFound("Pedido no encontrado."));
            if (!idEmpresa.equals(pedido.getIdEmpresa())) {
                throw ApiException.badRequest("El pedido no pertenece a la empresa de esta caja.");
            }
            if (!idSucursal.equals(pedido.getIdSucursal())) {
                throw ApiException.badRequest("El pedido no pertenece a la sucursal de esta caja.");
            }
            if (pedido.getIdVenta() != null) {
                throw ApiException.conflict("El pedido ya fue convertido en venta.", "El pedido ya fue convertido en venta.");
            }
            if (!"listo_para_recoger".equals(pedido.getEstado())) {
                throw ApiException.badRequest("El pedido debe estar 'listo_para_recoger' para ser facturado. Estado actual: " + pedido.getEstado());
            }
        }

        String estado = esCredito ? "CREDITO" : (!pagosPayload.isEmpty() ? "PAGADA" : "PENDIENTE_COBRO");
        Venta venta = new Venta();
        venta.setTipoVenta(tipoVenta);
        venta.setIdCliente(payload.getIdCliente());
        venta.setIdCajaSesion(idCajaSesion);
        venta.setIdUsuario(usuario.getIdUsuario());
        venta.setIdPedido(payload.getIdPedido());
        venta.setSubtotal(nvl(payload.getSubtotal()));
        venta.setDescuentoTotal(nvl(payload.getDescuentoTotal()));
        venta.setTotal(nvl(payload.getTotal()));
        venta.setFecha(LocalDateTime.now());
        venta.setEstado(estado);
        venta = ventaRepository.save(venta);

        if (pedido != null) {
            pedido.setIdVenta(venta.getIdVenta());
            pedido.setEstado("convertido_en_venta");
            pedidoRepository.save(pedido);
        }

        TipoMovimiento tipoVentaInv = tipoMovimientoRepository.findByNombreIgnoreCase("Venta")
                .or(() -> tipoMovimientoRepository.findByNombre("Venta"))
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Tipo de movimiento de inventario \"Venta\" no configurado."));

        if (payload.getDetalles() == null || payload.getDetalles().isEmpty()) {
            throw ApiException.badRequest("La venta debe tener detalles.");
        }
        for (VentaDtos.DetalleCreate d : payload.getDetalles()) {
            Producto producto = productoRepository.findById(d.getIdProducto())
                    .orElseThrow(() -> ApiException.notFound("Producto " + d.getIdProducto() + " no encontrado."));
            DetalleVenta detalle = new DetalleVenta();
            detalle.setVenta(venta);
            detalle.setIdProducto(d.getIdProducto());
            detalle.setCantidad(d.getCantidad());
            detalle.setPrecioUnitario(d.getPrecioUnitario());
            detalle.setDescuento(nvl(d.getDescuento()));
            detalle.setSubtotal(d.getSubtotal());
            detalle.setTotal(d.getSubtotal());
            detalle.setDescripcion(d.getDescripcion());
            detalleVentaRepository.save(detalle);

            InventarioDtos.MovimientoCreate mov = new InventarioDtos.MovimientoCreate();
            mov.setIdProducto(producto.getIdProducto());
            mov.setIdTipoMovimiento(tipoVentaInv.getIdTipoMovimiento());
            mov.setCantidad(d.getCantidad());
            mov.setObservacion("Movimiento de la venta " + venta.getIdVenta());
            inventarioService.crearMovimiento(usuario, idEmpresa, idSucursal, mov);
        }

        CuentaPorCobrar cuenta = null;
        if (esCredito && cliente != null && cliente.getCategoriaCliente() != null) {
            LocalDateTime inicio = LocalDateTime.now();
            cuenta = new CuentaPorCobrar();
            cuenta.setVenta(venta);
            cuenta.setMontoCredito(venta.getTotal());
            cuenta.setSaldoPendiente(venta.getTotal());
            cuenta.setFechaInicio(inicio);
            cuenta.setFechaVencimiento(inicio.plusDays(cliente.getCategoriaCliente().getPlazoCredito() == null
                    ? 0 : cliente.getCategoriaCliente().getPlazoCredito()));
            cuenta.setEstado("PENDIENTE");
            cuenta = cuentaPorCobrarRepository.save(cuenta);
            cliente.setSaldoCredito(cuentaPorCobrarRepository.sumSaldoPendienteByCliente(cliente.getIdCliente()));
            clienteRepository.save(cliente);
        }

        TipoMovimientoCaja tipoIngreso = tipoMovimientoCajaRepository.findByNombreIgnoreCase("INGRESO").orElse(null);
        if (!esCredito) {
            for (VentaDtos.PagoCreate pago : pagosPayload) {
                VentaPago ventaPago = new VentaPago();
                ventaPago.setIdVenta(venta.getIdVenta());
                ventaPago.setIdMetodoPago(pago.getIdMetodoPago());
                ventaPago.setVenta(venta);
                ventaPago.setMonto(pago.getMonto());
                ventaPago.setFecha(LocalDateTime.now());
                ventaPagoRepository.save(ventaPago);
                if (tipoIngreso != null) {
                    MovimientoCaja movimientoCaja = new MovimientoCaja();
                    movimientoCaja.setIdMetodoPago(pago.getIdMetodoPago());
                    movimientoCaja.setTipoMovimientoCaja(tipoIngreso);
                    movimientoCaja.setCajaSesion(sesion);
                    movimientoCaja.setIdUsuario(usuario.getIdUsuario());
                    movimientoCaja.setFecha(LocalDateTime.now());
                    movimientoCaja.setMonto(pago.getMonto());
                    movimientoCaja.setConcepto("Venta " + venta.getIdVenta());
                    movimientoCajaRepository.save(movimientoCaja);
                }
            }
        }

        if (esCredito && cuenta != null && cliente != null) {
            try {
                notificationService.enviarNotificacionUsuario(
                        cliente.getIdUsuario(),
                        idEmpresa,
                        "Nueva compra registrada",
                        "Se registró una nueva compra por Bs. " + venta.getTotal()
                                + ". Su saldo pendiente es de Bs. " + cuenta.getSaldoPendiente() + ".",
                        Map.of(
                                "id_cxc", String.valueOf(cuenta.getIdCxc()),
                                "id_empresa", String.valueOf(idEmpresa),
                                "id_usuario", String.valueOf(cliente.getIdUsuario()),
                                "monto_credito", String.valueOf(cuenta.getMontoCredito()),
                                "saldo_pendiente", String.valueOf(cuenta.getSaldoPendiente()),
                                "estado", cuenta.getEstado()));
            } catch (Exception ignored) {
            }
        }
        return toVentaResponse(ventaRepository.findById(venta.getIdVenta()).orElse(venta));
    }

    @Transactional(readOnly = true)
    public List<VentaDtos.VentaResponse> historial(Usuario usuario, Integer idCajaSesion) {
        requireSesionDelUsuario(usuario, idCajaSesion);
        return ventaRepository.findByIdCajaSesionOrderByFechaDesc(idCajaSesion).stream()
                .map(this::toVentaResponse)
                .toList();
    }

    @Transactional
    public VentaDtos.CobroResponse registrarPagoCredito(Usuario usuario, Integer idCajaSesion, VentaDtos.CobroCreate payload) {
        CajaSesion sesion = requireSesionAbierta(usuario, idCajaSesion);
        Integer idEmpresaSesion = sesion.getCaja().getSucursal().getEmpresa().getIdEmpresa();
        CuentaPorCobrar cuenta = cuentaPorCobrarRepository.findById(payload.getIdCxc())
                .orElseThrow(() -> ApiException.notFound("Cuenta por cobrar no encontrada."));
        CajaSesion sesionVenta = cajaSesionRepository.findWithCajaSucursalEmpresa(cuenta.getVenta().getIdCajaSesion())
                .orElseThrow(() -> ApiException.notFound("Caja sesion no encontrada."));
        Integer idEmpresaCuenta = sesionVenta.getCaja().getSucursal().getEmpresa().getIdEmpresa();
        if (!idEmpresaSesion.equals(idEmpresaCuenta)) {
            throw ApiException.badRequest("La cuenta por cobrar no pertenece a la empresa de la caja sesion.");
        }
        BigDecimal saldoAnterior = nvl(cuenta.getSaldoPendiente());
        if (saldoAnterior.compareTo(BigDecimal.ZERO) <= 0 || "PAGADA".equals(cuenta.getEstado())) {
            throw ApiException.badRequest("La cuenta por cobrar ya se encuentra pagada.");
        }
        List<VentaDtos.PagoCreditoItem> pagos = payload.getPagosCredito();
        if (pagos == null || pagos.isEmpty()) {
            throw ApiException.badRequest("Debe enviar al menos un pago.");
        }
        Set<Integer> ids = new HashSet<>();
        BigDecimal totalPagado = BigDecimal.ZERO;
        Set<String> permitidos = Set.of("EFECTIVO", "QR", "TARJETA");
        for (VentaDtos.PagoCreditoItem pago : pagos) {
            if (!ids.add(pago.getIdMetodoPago())) {
                throw ApiException.badRequest("No se puede repetir el mismo metodo de pago.");
            }
            MetodoPago metodo = metodoPagoRepository.findById(pago.getIdMetodoPago())
                    .orElseThrow(() -> ApiException.notFound("Uno o mas metodos de pago no fueron encontrados."));
            String nombre = (metodo.getNombre() == null ? "" : metodo.getNombre()).trim().toUpperCase(Locale.ROOT);
            if (!permitidos.contains(nombre)) {
                throw ApiException.badRequest("El metodo de pago " + metodo.getNombre() + " no esta permitido para pagos de credito.");
            }
            if (pago.getMontoPagado() == null || pago.getMontoPagado().compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest("El monto pagado debe ser mayor a cero.");
            }
            totalPagado = totalPagado.add(pago.getMontoPagado());
        }
        if (totalPagado.compareTo(saldoAnterior) > 0) {
            throw ApiException.badRequest("El total pagado no puede ser mayor al saldo pendiente.");
        }
        TipoMovimientoCaja tipoIngreso = tipoMovimientoCajaRepository.findByNombreIgnoreCase("INGRESO")
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Tipo de movimiento de caja INGRESO no configurado."));

        LocalDateTime ahora = LocalDateTime.now();
        List<PagoCredito> pagosCreados = new ArrayList<>();
        List<MovimientoCaja> movimientos = new ArrayList<>();
        for (VentaDtos.PagoCreditoItem pago : pagos) {
            PagoCredito creado = new PagoCredito();
            creado.setCuentaPorCobrar(cuenta);
            creado.setIdMetodoPago(pago.getIdMetodoPago());
            creado.setMontoPagado(pago.getMontoPagado());
            creado.setFechaPago(ahora);
            pagosCreados.add(pagoCreditoRepository.save(creado));

            MovimientoCaja movimiento = new MovimientoCaja();
            movimiento.setIdMetodoPago(pago.getIdMetodoPago());
            movimiento.setTipoMovimientoCaja(tipoIngreso);
            movimiento.setCajaSesion(sesion);
            movimiento.setIdUsuario(usuario.getIdUsuario());
            movimiento.setFecha(ahora);
            movimiento.setMonto(pago.getMontoPagado());
            movimiento.setConcepto("PAGO_CREDITO CXC " + cuenta.getIdCxc());
            movimientos.add(movimientoCajaRepository.save(movimiento));
        }
        cuenta.setSaldoPendiente(saldoAnterior.subtract(totalPagado));
        if (cuenta.getSaldoPendiente().compareTo(BigDecimal.ZERO) == 0) {
            cuenta.setEstado("PAGADA");
        }
        cuentaPorCobrarRepository.save(cuenta);

        if (cuenta.getVenta().getIdCliente() != null) {
            clienteRepository.findById(cuenta.getVenta().getIdCliente()).ifPresent(cli -> {
                cli.setSaldoCredito(cuentaPorCobrarRepository.sumSaldoPendienteByCliente(cli.getIdCliente()));
                clienteRepository.save(cli);
            });
        }

        BigDecimal saldoPendienteFinal = cuenta.getSaldoPendiente();
        String estadoFinal = cuenta.getEstado();
        Integer idCxc = cuenta.getIdCxc();
        BigDecimal totalPagadoFinal = totalPagado;
        if (cuenta.getVenta().getIdCliente() != null) {
            clienteRepository.findById(cuenta.getVenta().getIdCliente()).ifPresent(cliente -> {
                try {
                    notificationService.enviarNotificacionUsuario(
                            cliente.getIdUsuario(),
                            idEmpresaSesion,
                            "Abono de Crédito Registrado",
                            "Se ha registrado un abono de " + totalPagadoFinal + " a tu crédito. Saldo pendiente: "
                                    + saldoPendienteFinal + ".",
                            Map.of(
                                    "id_cxc", String.valueOf(idCxc),
                                    "id_empresa", String.valueOf(idEmpresaSesion),
                                    "id_usuario", String.valueOf(cliente.getIdUsuario()),
                                    "monto_pagado", String.valueOf(totalPagadoFinal),
                                    "saldo_pendiente", String.valueOf(saldoPendienteFinal),
                                    "estado", estadoFinal));
                } catch (Exception ignored) {
                }
            });
        }

        VentaDtos.CobroResponse response = new VentaDtos.CobroResponse();
        response.setIdCxc(cuenta.getIdCxc());
        response.setIdCajaSesion(idCajaSesion);
        response.setMontoCredito(cuenta.getMontoCredito());
        response.setSaldoAnterior(saldoAnterior);
        response.setTotalPagado(totalPagado);
        response.setSaldoPendiente(cuenta.getSaldoPendiente());
        response.setEstado(cuenta.getEstado());
        response.setPagosCredito(pagosCreados.stream().map(this::toPagoCredito).toList());
        response.setMovimientosCaja(movimientos.stream().map(this::toMovimientoCaja).toList());
        return response;
    }

    @Transactional(readOnly = true)
    public List<VentaDtos.CuentaPorCobrarResponse> cuentasPorCobrar(Usuario usuario, Integer idEmpresa, Integer idCliente) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> ApiException.notFound("Cliente no encontrado."));
        boolean operador = usuarioRolRepository.existsByUsuario_IdUsuarioAndIdEmpresaAndActivoTrue(
                usuario.getIdUsuario(), idEmpresa);
        if (!operador && !cliente.getIdUsuario().equals(usuario.getIdUsuario())) {
            throw ApiException.forbidden("No tiene permiso para consultar las cuentas por cobrar de este cliente.");
        }
        List<VentaDtos.CuentaPorCobrarResponse> result = new ArrayList<>();
        for (CuentaPorCobrar cuenta : cuentaPorCobrarRepository.findByVenta_IdCliente(idCliente)) {
            CajaSesion sesion = cajaSesionRepository.findWithCajaSucursalEmpresa(cuenta.getVenta().getIdCajaSesion())
                    .orElse(null);
            if (sesion == null || !idEmpresa.equals(sesion.getCaja().getSucursal().getEmpresa().getIdEmpresa())) {
                continue;
            }
            VentaDtos.CuentaPorCobrarResponse dto = new VentaDtos.CuentaPorCobrarResponse();
            dto.setIdCxc(cuenta.getIdCxc());
            dto.setIdVenta(cuenta.getVenta().getIdVenta());
            dto.setMontoCredito(cuenta.getMontoCredito());
            dto.setSaldoPendiente(cuenta.getSaldoPendiente());
            dto.setFechaInicio(cuenta.getFechaInicio());
            dto.setFechaVencimiento(cuenta.getFechaVencimiento());
            dto.setEstado(cuenta.getEstado());
            dto.setVenta(toVentaResponse(cuenta.getVenta()));
            dto.setPagosCredito(pagoCreditoRepository.findByCuentaPorCobrar_IdCxc(cuenta.getIdCxc())
                    .stream().map(this::toPagoCredito).toList());
            result.add(dto);
        }
        return result;
    }

    private CajaSesion requireSesionAbierta(Usuario usuario, Integer idCajaSesion) {
        CajaSesion sesion = requireSesionDelUsuario(usuario, idCajaSesion);
        if (!"Abierto".equalsIgnoreCase(sesion.getEstado())) {
            throw ApiException.badRequest("La caja sesion debe estar abierta.");
        }
        return sesion;
    }

    private CajaSesion requireSesionDelUsuario(Usuario usuario, Integer idCajaSesion) {
        CajaSesion sesion = cajaSesionRepository.findWithCajaSucursalEmpresa(idCajaSesion)
                .orElseThrow(() -> ApiException.notFound("Caja sesion no encontrada."));
        if (!usuario.getIdUsuario().equals(sesion.getIdUsuario())) {
            throw ApiException.forbidden("La caja sesion no fue creada por el usuario actual.");
        }
        return sesion;
    }

    private VentaDtos.VentaResponse toVentaResponse(Venta venta) {
        VentaDtos.VentaResponse dto = new VentaDtos.VentaResponse();
        dto.setIdVenta(venta.getIdVenta());
        dto.setIdUsuario(venta.getIdUsuario());
        dto.setIdCajaSesion(venta.getIdCajaSesion());
        dto.setIdCliente(venta.getIdCliente());
        dto.setIdTipoVenta(venta.getTipoVenta() == null ? null : venta.getTipoVenta().getIdTipoVenta());
        dto.setSubtotal(venta.getSubtotal());
        dto.setDescuentoTotal(venta.getDescuentoTotal());
        dto.setTotal(venta.getTotal());
        dto.setFecha(venta.getFecha());
        dto.setEstado(venta.getEstado());
        dto.setTipoVentaNombre(venta.getTipoVenta() == null ? null : venta.getTipoVenta().getNombre());
        List<VentaDtos.DetalleResponse> detalles = new ArrayList<>();
        for (DetalleVenta detalle : detalleVentaRepository.findByVenta_IdVenta(venta.getIdVenta())) {
            VentaDtos.DetalleResponse item = new VentaDtos.DetalleResponse();
            item.setIdDetalleVenta(detalle.getIdDetalleVenta());
            item.setIdProducto(detalle.getIdProducto());
            item.setCantidad(detalle.getCantidad());
            item.setPrecioUnitario(detalle.getPrecioUnitario());
            item.setDescuento(detalle.getDescuento());
            item.setSubtotal(detalle.getSubtotal());
            item.setTotal(detalle.getTotal());
            item.setDescripcion(detalle.getDescripcion());
            productoRepository.findById(detalle.getIdProducto()).ifPresent(producto -> {
                VentaDtos.ProductoSimple simple = new VentaDtos.ProductoSimple();
                simple.setIdProducto(producto.getIdProducto());
                simple.setNombre(producto.getNombre());
                simple.setCodigoBarra(producto.getCodigoBarra());
                simple.setUnidadMedida(producto.getUnidadMedida());
                item.setProducto(simple);
            });
            detalles.add(item);
        }
        dto.setDetalles(detalles);
        List<VentaDtos.PagoResponse> pagos = new ArrayList<>();
        for (VentaPago pago : ventaPagoRepository.findByIdVenta(venta.getIdVenta())) {
            VentaDtos.PagoResponse item = new VentaDtos.PagoResponse();
            item.setIdMetodoPago(pago.getIdMetodoPago());
            item.setMonto(pago.getMonto());
            item.setFecha(pago.getFecha());
            metodoPagoRepository.findById(pago.getIdMetodoPago()).ifPresent(metodo -> item.setMetodoPago(toMetodo(metodo)));
            pagos.add(item);
        }
        dto.setPagos(pagos);
        if (!pagos.isEmpty()) {
            dto.setIdMetodoPago(pagos.get(0).getIdMetodoPago());
            dto.setMetodoPago(pagos.get(0).getMetodoPago());
        }
        return dto;
    }

    private VentaDtos.CatalogoItem toMetodo(MetodoPago metodo) {
        VentaDtos.CatalogoItem item = new VentaDtos.CatalogoItem();
        item.setIdMetodoPago(metodo.getIdMetodoPago());
        item.setNombre(metodo.getNombre());
        item.setDescripcion(metodo.getDescripcion());
        return item;
    }

    private VentaDtos.PagoCreditoResponse toPagoCredito(PagoCredito pago) {
        VentaDtos.PagoCreditoResponse dto = new VentaDtos.PagoCreditoResponse();
        dto.setIdPagoCredito(pago.getIdPagoCredito());
        dto.setIdMetodoPago(pago.getIdMetodoPago());
        dto.setMontoPagado(pago.getMontoPagado());
        dto.setFechaPago(pago.getFechaPago());
        metodoPagoRepository.findById(pago.getIdMetodoPago()).ifPresent(metodo -> dto.setMetodoPago(toMetodo(metodo)));
        return dto;
    }

    private VentaDtos.MovimientoCajaResponse toMovimientoCaja(MovimientoCaja movimiento) {
        VentaDtos.MovimientoCajaResponse dto = new VentaDtos.MovimientoCajaResponse();
        dto.setIdMovimientoCaja(movimiento.getIdMovimientoCaja());
        dto.setIdMetodoPago(movimiento.getIdMetodoPago());
        dto.setIdTipoMovimientoCaja(movimiento.getTipoMovimientoCaja().getIdTipoMovimientoCaja());
        dto.setIdCajaSesion(movimiento.getCajaSesion().getIdCajaSesion());
        dto.setIdUsuario(movimiento.getIdUsuario());
        dto.setFecha(movimiento.getFecha());
        dto.setMonto(movimiento.getMonto());
        dto.setConcepto(movimiento.getConcepto());
        return dto;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
