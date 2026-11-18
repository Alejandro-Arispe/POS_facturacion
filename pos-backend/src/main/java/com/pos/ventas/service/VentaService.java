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
import com.pos.ventas.domain.AutorizacionCredito;
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
    private final SalePricing pricing;
    private final com.pos.ventas.repo.PedidoClienteDetalleRepository orderDetails;
    private final com.pos.common.access.RbacResolver rbac;
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
    private final com.pos.siat.service.SiatFacturaService siatFacturaService;
    private final com.pos.ventas.repo.FacturaRepository facturaRepository;
    @org.springframework.beans.factory.annotation.Autowired private com.pos.ventas.repo.AutorizacionCreditoRepository autorizacionCreditoRepository;
    @org.springframework.beans.factory.annotation.Autowired private com.pos.usuarios.repo.UsuarioRepository usuarioRepository;
    @org.springframework.beans.factory.annotation.Autowired private com.pos.common.security.PasswordService passwordService;
    @org.springframework.beans.factory.annotation.Autowired private com.pos.auditoria.service.BitacoraService bitacoraService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.pos.ventas.repo.PagoCreditoReportadoRepository pagosReportados;

    public VentaService(
            EmpresaAccess empresaAccess, SalePricing pricing, com.pos.common.access.RbacResolver rbac, com.pos.ventas.repo.PedidoClienteDetalleRepository orderDetails,
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
            UsuarioRolRepository usuarioRolRepository,
            com.pos.siat.service.SiatFacturaService siatFacturaService,
            com.pos.ventas.repo.FacturaRepository facturaRepository) {
        this.empresaAccess = empresaAccess; this.pricing=pricing; this.rbac=rbac; this.orderDetails=orderDetails;
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
        this.siatFacturaService = siatFacturaService;
        this.facturaRepository = facturaRepository;
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
        empresaAccess.requirePermiso(usuario,idEmpresa,idSucursal,"sale:create");
        if (Boolean.TRUE.equals(payload.getFacturaLinea())) empresaAccess.requirePermiso(usuario,idEmpresa,idSucursal,"invoice:issue");
        // HU-08: el descuento base de la categoría del cliente se aplica en la misma transacción de la venta.
        BigDecimal descuentoCategoria = payload.getIdCliente() == null || payload.getIdCliente() <= 0 ? BigDecimal.ZERO
                : clienteRepository.findById(payload.getIdCliente())
                        .filter(c -> Boolean.TRUE.equals(c.getActivo()) && belongsToCompany(c, idEmpresa))
                        .map(Cliente::getCategoriaCliente).map(com.pos.clientes.domain.CategoriaCliente::getDescuentoBase)
                        .orElse(BigDecimal.ZERO);
        SalePricing.Quote quote = pricing.calculate(idEmpresa,payload,rbac.resolve(usuario,idEmpresa,idSucursal),descuentoCategoria);

        TipoVenta tipoVenta = tipoVentaRepository.findById(payload.getIdTipoVenta())
                .orElseThrow(() -> ApiException.notFound("Tipo de venta no encontrado."));
        String nombreTipo = (tipoVenta.getNombre() == null ? "" : tipoVenta.getNombre()).trim().toUpperCase(Locale.ROOT);
        boolean esCredito = nombreTipo.equals("CREDITO") || nombreTipo.equals("CRÉDITO");

        List<VentaDtos.PagoCreate> pagosPayload = payload.getPagos() == null ? new ArrayList<>() : new ArrayList<>(payload.getPagos());
        if (pagosPayload.isEmpty() && payload.getIdMetodoPago() != null) {
            VentaDtos.PagoCreate unico = new VentaDtos.PagoCreate();
            unico.setIdMetodoPago(payload.getIdMetodoPago());
            unico.setMonto(quote.total());
            pagosPayload.add(unico);
        }
        if (esCredito && !pagosPayload.isEmpty()) {
            throw ApiException.badRequest("Una venta a credito no debe enviar pagos ni metodo de pago.");
        }

        Set<Integer> idsMetodos = new HashSet<>();
        BigDecimal totalPagado = BigDecimal.ZERO;
        BigDecimal montoSaldoAFavor = BigDecimal.ZERO;
        for (VentaDtos.PagoCreate pago : pagosPayload) {
            if (pago == null) throw ApiException.badRequest("Pago inválido.");
            SalePricing.money(pago.getMonto(),"pago");
            if (pago.getIdMetodoPago() == null || pago.getIdMetodoPago() <= 0) {
                throw ApiException.badRequest("Metodo de pago invalido.");
            }
            if (!idsMetodos.add(pago.getIdMetodoPago())) {
                throw ApiException.badRequest("No se puede repetir el mismo metodo de pago en una venta.");
            }
            if (pago.getMonto() == null || pago.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest("El monto de cada pago debe ser mayor a cero.");
            }
            MetodoPago metodoPago = metodoPagoRepository.findById(pago.getIdMetodoPago())
                    .orElseThrow(() -> ApiException.notFound("Metodo de pago " + pago.getIdMetodoPago() + " no encontrado."));
            if (esSaldoAFavor(metodoPago)) montoSaldoAFavor = montoSaldoAFavor.add(pago.getMonto());
            totalPagado = totalPagado.add(pago.getMonto());
        }
        if (!pagosPayload.isEmpty() && totalPagado.compareTo(quote.total()) != 0) {
            throw ApiException.badRequest("La suma de los pagos debe ser igual al total de la venta.");
        }

        if (!esCredito && pagosPayload.isEmpty() && quote.total().signum()>0) throw ApiException.badRequest("Una venta al contado debe incluir sus pagos.");
        Cliente cliente = null;
        if (payload.getIdCliente() != null) {
            if (payload.getIdCliente() <= 0) {
                throw ApiException.badRequest("Cliente invalido. Envie null si la venta no tiene cliente.");
            }
            cliente = clienteRepository.findById(payload.getIdCliente())
                    .orElseThrow(() -> ApiException.notFound("Cliente no encontrado."));
            if (!Boolean.TRUE.equals(cliente.getActivo()) || !belongsToCompany(cliente,idEmpresa))
                throw ApiException.notFound("Cliente no encontrado en esta empresa.");
        }
        // HU-15: el saldo a favor por devoluciones se usa como medio de pago del mismo cliente.
        if (montoSaldoAFavor.signum() > 0) {
            if (cliente == null) throw ApiException.badRequest("El pago con saldo a favor requiere seleccionar al cliente.");
            cliente = clienteRepository.lockById(cliente.getIdCliente()).orElseThrow(() -> ApiException.notFound("Cliente no encontrado."));
            BigDecimal disponible = nvl(cliente.getSaldoAFavor());
            if (montoSaldoAFavor.compareTo(disponible) > 0)
                throw ApiException.badRequest("El cliente solo tiene Bs " + disponible + " de saldo a favor.");
            cliente.setSaldoAFavor(disponible.subtract(montoSaldoAFavor));
            clienteRepository.save(cliente);
        }
        Usuario adminAutorizador = null;
        if (esCredito) {
            if (cliente == null) {
                throw ApiException.badRequest("Una venta a credito debe tener cliente.");
            }
            clienteRepository.lockById(cliente.getIdCliente()).orElseThrow(() -> ApiException.notFound("Cliente no encontrado."));
            if (cliente.getCategoriaCliente() == null) {
                throw ApiException.badRequest("El cliente no tiene categoria asociada para calcular el plazo de credito.");
            }
            BigDecimal limit=cliente.getLimiteCredito()!=null && cliente.getLimiteCredito().signum()>0 ? cliente.getLimiteCredito() : nvl(cliente.getCategoriaCliente().getLimiteCredito());
            if(cuentaPorCobrarRepository.sumSaldoPendienteByCliente(cliente.getIdCliente()).add(quote.total()).compareTo(limit)>0)
                adminAutorizador = autorizadorExcesoCredito(payload.getAutorizacionCredito(), idEmpresa, idSucursal);
        }

        PedidoCliente pedido = null;
        if (payload.getIdPedido() != null) {
            empresaAccess.requirePermiso(usuario,idEmpresa,idSucursal,"order:manage");
            pedido = pedidoRepository.findForUpdate(payload.getIdPedido())
                    .orElseThrow(() -> ApiException.notFound("Pedido no encontrado."));
            if (!idEmpresa.equals(pedido.getIdEmpresa())) {
                throw ApiException.badRequest("El pedido no pertenece a la empresa de esta caja.");
            }
            if (!idSucursal.equals(pedido.getIdSucursal())) {
                throw ApiException.badRequest("El pedido no pertenece a la sucursal de esta caja.");
            }
            if (!java.util.Objects.equals(pedido.getIdCliente(),payload.getIdCliente())) throw ApiException.badRequest("El cliente debe coincidir con el pedido.");
            if (pedido.getIdVenta() != null) {
                throw ApiException.conflict("El pedido ya fue convertido en venta.", "El pedido ya fue convertido en venta.");
            }
            if (!"listo_para_recoger".equals(pedido.getEstado())) {
                throw ApiException.badRequest("El pedido debe estar 'listo_para_recoger' para ser facturado. Estado actual: " + pedido.getEstado());
            }
            Map<Integer,Integer> expected=new java.util.LinkedHashMap<>();
            for(var row:orderDetails.findByPedido_IdPedido(pedido.getIdPedido())) expected.merge(row.getIdProducto(),row.getCantidad(),Math::addExact);
            Map<Integer,Integer> actual=new java.util.LinkedHashMap<>();
            for(var line:quote.lines()) actual.put(line.product().getIdProducto(),line.quantity());
            if(!expected.equals(actual)) throw ApiException.badRequest("Los productos y cantidades deben coincidir con el pedido.");
        }

        String estado = esCredito ? "CREDITO" : (!pagosPayload.isEmpty() ? "PAGADA" : "PENDIENTE_COBRO");
        Venta venta = new Venta();
        venta.setTipoVenta(tipoVenta);
        venta.setIdCliente(payload.getIdCliente());
        venta.setIdCajaSesion(idCajaSesion);
        venta.setIdUsuario(usuario.getIdUsuario());
        venta.setIdPedido(payload.getIdPedido());
        venta.setSubtotal(quote.subtotal());
        venta.setDescuentoTotal(quote.discount());
        venta.setTotal(quote.total());
        venta.setFecha(LocalDateTime.now());
        venta.setEstado(estado);
        venta = ventaRepository.save(venta);

        if (pedido != null) {
            pedido.setIdVenta(venta.getIdVenta());
            pedido.setEstado("convertido_en_venta");
            pedidoRepository.save(pedido);
        }

        for (SalePricing.Line line : quote.lines().stream().sorted(java.util.Comparator.comparing(l -> l.product().getIdProducto())).toList()) {
            DetalleVenta detalle = new DetalleVenta();
            detalle.setVenta(venta);
            detalle.setIdProducto(line.product().getIdProducto());
            detalle.setCantidad(line.quantity());
            detalle.setPrecioUnitario(line.price());
            detalle.setDescuento(line.discount());
            detalle.setSubtotal(line.net());
            detalle.setTotal(line.net());
            detalle.setDescripcion(line.description());
            detalle.setCodigoProductoSin(line.product().getCodigoProductoSin());
            detalle.setCodigoUnidadMedidaSin(line.product().getCodigoUnidadMedidaSin());
            detalleVentaRepository.save(detalle);
            inventarioService.registrarSalidaVenta(usuario,idEmpresa,idSucursal,line.product().getIdProducto(),line.quantity(),venta.getIdVenta());
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

        TipoMovimientoCaja tipoIngreso = tipoMovimientoCajaRepository.findByNombreIgnoreCase("INGRESO")
                .orElseThrow(() -> ApiException.badRequest("Tipo de movimiento INGRESO no configurado."));
        if (!esCredito) {
            for (VentaDtos.PagoCreate pago : pagosPayload) {
                VentaPago ventaPago = new VentaPago();
                ventaPago.setIdVenta(venta.getIdVenta());
                ventaPago.setIdMetodoPago(pago.getIdMetodoPago());
                ventaPago.setVenta(venta);
                ventaPago.setMonto(pago.getMonto());
                ventaPago.setFecha(LocalDateTime.now());
                ventaPagoRepository.save(ventaPago);
                // El saldo a favor no es dinero que entra a la caja.
                boolean saldoAFavor = metodoPagoRepository.findById(pago.getIdMetodoPago()).map(this::esSaldoAFavor).orElse(false);
                if (tipoIngreso != null && !saldoAFavor) {
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

        if (Boolean.TRUE.equals(payload.getFacturaLinea())) {
            // Facturación en línea SIAT (simulada): el método de pago informado es el de mayor monto.
            String metodoPrincipal = pagosPayload.stream()
                    .max(java.util.Comparator.comparing(VentaDtos.PagoCreate::getMonto))
                    .flatMap(p -> metodoPagoRepository.findById(p.getIdMetodoPago()))
                    .map(MetodoPago::getNombre).orElse(null);
            siatFacturaService.emitir(venta, sesion, cliente,
                    new com.pos.siat.service.SiatFacturaService.Comprador(
                            payload.getCodigoTipoDocumento(), payload.getNumeroDocumento(), payload.getRazonSocial()),
                    metodoPrincipal);
        }

        if (adminAutorizador != null) {
            AutorizacionCredito autorizacion = new AutorizacionCredito();
            autorizacion.setVenta(venta);
            autorizacion.setIdUsuarioAdmin(adminAutorizador.getIdUsuario());
            autorizacion.setFecha(LocalDateTime.now());
            autorizacion.setMotivo(payload.getAutorizacionCredito().getMotivo().trim());
            autorizacionCreditoRepository.save(autorizacion);
            bitacoraService.registrar(new com.pos.auditoria.service.BitacoraService.Evento(
                    adminAutorizador, adminAutorizador.getEmail(), idEmpresa, "AUTORIZAR_CREDITO", "VENTAS", null, null, 200,
                    "Autorizó exceder el límite de crédito en la venta " + venta.getIdVenta() + " (cliente " + cliente.getIdCliente()
                            + ", cajero " + usuario.getIdUsuario() + "): " + autorizacion.getMotivo()));
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

    /** HU-09: valida que quien autoriza sea administrador de la empresa (modelo RBAC) y sus credenciales. */
    private Usuario autorizadorExcesoCredito(VentaDtos.AutorizacionCreditoCreate datos, Integer idEmpresa, Integer idSucursal) {
        if (datos == null || datos.getEmail() == null || datos.getEmail().isBlank() || datos.getContrasena() == null)
            throw ApiException.badRequest("La venta supera el límite de crédito disponible del cliente. Requiere autorización de un administrador.");
        if (datos.getMotivo() == null || datos.getMotivo().isBlank())
            throw ApiException.badRequest("Indica el motivo de la autorización de crédito.");
        Usuario admin = usuarioRepository.findByEmailIgnoreCase(datos.getEmail().trim())
                .filter(u -> passwordService.matches(datos.getContrasena(), u.getContrasena()))
                .orElseThrow(() -> ApiException.forbidden("Credenciales de administrador inválidas."));
        if (!rbac.hasBranchAccess(admin, idEmpresa, idSucursal)
                || rbac.assignments(admin, idEmpresa, idSucursal).stream().noneMatch(com.pos.common.access.RbacResolver::companyAdministrator))
            throw ApiException.forbidden("El usuario no tiene permiso de administrador para autorizar el crédito.");
        return admin;
    }

    @Transactional(readOnly = true)
    public List<VentaDtos.VentaResponse> historial(Usuario usuario, Integer idCajaSesion) {
        CajaSesion session=requireSesionDelUsuario(usuario, idCajaSesion);
        sessionPermission(usuario,session,"sale:read");
        return ventaRepository.findByIdCajaSesionOrderByFechaDesc(idCajaSesion).stream()
                .map(this::toVentaResponse)
                .toList();
    }

    @Transactional
    public VentaDtos.CobroResponse registrarPagoCredito(Usuario usuario, Integer idCajaSesion, VentaDtos.CobroCreate payload) {
        CajaSesion sesion = requireSesionAbierta(usuario, idCajaSesion);
        sessionPermission(usuario,sesion,"sale:collect");
        Integer idEmpresaSesion = sesion.getCaja().getSucursal().getEmpresa().getIdEmpresa();
        CuentaPorCobrar cuenta = cuentaPorCobrarRepository.findForUpdate(payload.getIdCxc())
                .orElseThrow(() -> ApiException.notFound("Cuenta por cobrar no encontrada."));
        CajaSesion sesionVenta = cajaSesionRepository.findWithCajaSucursalEmpresa(cuenta.getVenta().getIdCajaSesion())
                .orElseThrow(() -> ApiException.notFound("Caja sesion no encontrada."));
        Integer idEmpresaCuenta = sesionVenta.getCaja().getSucursal().getEmpresa().getIdEmpresa();
        if (!idEmpresaSesion.equals(idEmpresaCuenta)) {
            throw ApiException.badRequest("La cuenta por cobrar no pertenece a la empresa de la caja sesion.");
        }
        empresaAccess.requireSucursal(usuario,idEmpresaCuenta,sesionVenta.getCaja().getSucursal().getIdSucursal());
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
        Set<String> permitidos = Set.of("EFECTIVO", "QR", "TARJETA", "TRANSFERENCIA");
        for (VentaDtos.PagoCreditoItem pago : pagos) {
            if (pago == null || pago.getIdMetodoPago()==null) throw ApiException.badRequest("Pago inválido.");
            SalePricing.money(pago.getMontoPagado(),"pago");
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
        if (payload.getIdPagoReportado() != null) {
            com.pos.ventas.domain.PagoCreditoReportado reportado = pagosReportados.findById(payload.getIdPagoReportado())
                    .filter(r -> cuenta.getIdCxc().equals(r.getIdCxc()))
                    .orElseThrow(() -> ApiException.notFound("Pago informado no encontrado para esta cuenta."));
            if (!com.pos.ventas.domain.PagoCreditoReportado.PENDIENTE.equals(reportado.getEstado())) {
                throw ApiException.badRequest("El pago informado ya fue confirmado.");
            }
            reportado.setEstado(com.pos.ventas.domain.PagoCreditoReportado.CONFIRMADO);
            reportado.setIdUsuarioConfirma(usuario.getIdUsuario());
            reportado.setFechaConfirmacion(LocalDateTime.now());
            pagosReportados.save(reportado);
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

    /**
     * HU-10: el cliente informa un pago de su propio crédito. No modifica el saldo ni la caja: queda PENDIENTE
     * hasta que el personal registra el cobro en caja con id_pago_reportado.
     */
    @Transactional
    public VentaDtos.PagoReportadoResponse reportarPago(Usuario usuario, Integer idCxc, VentaDtos.PagoReportadoCreate payload) {
        empresaAccess.requireUsuarioActivo(usuario);
        CuentaPorCobrar cuenta = cuentaPorCobrarRepository.findForUpdate(idCxc)
                .orElseThrow(() -> ApiException.notFound("Cuenta por cobrar no encontrada."));
        Integer idCliente = cuenta.getVenta().getIdCliente();
        boolean own = idCliente != null && clienteRepository.findById(idCliente)
                .map(c -> java.util.Objects.equals(c.getIdUsuario(), usuario.getIdUsuario())).orElse(false);
        if (!own) throw ApiException.notFound("Cuenta por cobrar no encontrada.");
        if (payload == null || payload.getIdMetodoPago() == null) throw ApiException.badRequest("Debe indicar el método de pago.");
        SalePricing.money(payload.getMontoPagado(), "pago");
        if (payload.getMontoPagado() == null || payload.getMontoPagado().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("El monto pagado debe ser mayor a cero.");
        }
        MetodoPago metodo = metodoPagoRepository.findById(payload.getIdMetodoPago())
                .orElseThrow(() -> ApiException.notFound("Método de pago no encontrado."));
        String nombre = (metodo.getNombre() == null ? "" : metodo.getNombre()).trim().toUpperCase(Locale.ROOT);
        if (!Set.of("QR", "TARJETA", "TRANSFERENCIA").contains(nombre)) {
            throw ApiException.badRequest("El método de pago " + metodo.getNombre() + " no se puede informar desde la app.");
        }
        BigDecimal disponible = nvl(cuenta.getSaldoPendiente()).subtract(nvl(pagosReportados.sumPendienteByCxc(idCxc)));
        if ("PAGADA".equals(cuenta.getEstado()) || payload.getMontoPagado().compareTo(disponible) > 0) {
            throw ApiException.badRequest("El monto supera el saldo pendiente (descontando pagos ya informados).");
        }
        String referencia = payload.getReferencia() == null ? null : payload.getReferencia().trim();
        if (referencia != null && referencia.length() > 100) throw ApiException.badRequest("La referencia no puede superar 100 caracteres.");
        com.pos.ventas.domain.PagoCreditoReportado reportado = new com.pos.ventas.domain.PagoCreditoReportado();
        reportado.setIdCxc(idCxc);
        reportado.setIdUsuario(usuario.getIdUsuario());
        reportado.setIdMetodoPago(payload.getIdMetodoPago());
        reportado.setMonto(payload.getMontoPagado());
        reportado.setReferencia(referencia == null || referencia.isEmpty() ? null : referencia);
        reportado.setFechaReporte(LocalDateTime.now());
        return toPagoReportado(pagosReportados.save(reportado));
    }

    private VentaDtos.PagoReportadoResponse toPagoReportado(com.pos.ventas.domain.PagoCreditoReportado r) {
        VentaDtos.PagoReportadoResponse dto = new VentaDtos.PagoReportadoResponse();
        dto.setIdPagoReportado(r.getIdPagoReportado());
        dto.setIdCxc(r.getIdCxc());
        dto.setIdMetodoPago(r.getIdMetodoPago());
        dto.setMonto(r.getMonto());
        dto.setReferencia(r.getReferencia());
        dto.setEstado(r.getEstado());
        dto.setFechaReporte(r.getFechaReporte());
        dto.setFechaConfirmacion(r.getFechaConfirmacion());
        return dto;
    }

    /** HU-11: compras del cliente autenticado (ventas de todos sus perfiles de cliente). */
    @Transactional(readOnly = true)
    public List<VentaDtos.VentaResponse> misVentas(Usuario usuario) {
        empresaAccess.requireUsuarioActivo(usuario);
        return clienteRepository.findByIdUsuario(usuario.getIdUsuario()).stream()
                .flatMap(c -> ventaRepository.findByIdCliente(c.getIdCliente()).stream())
                .sorted(java.util.Comparator.comparing(com.pos.ventas.domain.Venta::getFecha,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(this::toVentaResponse)
                .toList();
    }

    /**
     * HU-11: compras de un cliente en la empresa, de la mas reciente a la mas antigua. Excluye las ventas
     * anuladas (regla de la HU) y admite un rango de fechas. Las ve el personal con "sale:read" o el propio cliente.
     */
    @Transactional(readOnly = true)
    public List<VentaDtos.VentaResponse> historialCliente(Usuario usuario, Integer idEmpresa, Integer idCliente,
            java.time.LocalDate desde, java.time.LocalDate hasta) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> ApiException.notFound("Cliente no encontrado."));
        if (!belongsToCompany(cliente, idEmpresa)) throw ApiException.notFound("Cliente no encontrado en esta empresa.");
        boolean own = java.util.Objects.equals(cliente.getIdUsuario(), usuario.getIdUsuario());
        if (!own && !rbac.resolve(usuario, idEmpresa, null).allows("sale:read"))
            throw ApiException.forbidden("No tiene permiso para consultar las compras de este cliente.");
        if (desde != null && hasta != null && desde.isAfter(hasta))
            throw ApiException.badRequest("La fecha inicial no puede ser posterior a la final.");
        LocalDateTime inicio = desde == null ? null : desde.atStartOfDay();
        LocalDateTime fin = hasta == null ? null : hasta.plusDays(1).atStartOfDay();
        return ventaRepository.findByIdCliente(idCliente).stream()
                .filter(v -> !"ANULADA".equalsIgnoreCase(v.getEstado()))
                .filter(v -> inicio == null || !v.getFecha().isBefore(inicio))
                .filter(v -> fin == null || v.getFecha().isBefore(fin))
                .filter(v -> cajaSesionRepository.findWithCajaSucursalEmpresa(v.getIdCajaSesion())
                        .map(s -> idEmpresa.equals(s.getCaja().getSucursal().getEmpresa().getIdEmpresa())).orElse(false))
                .sorted(java.util.Comparator.comparing(Venta::getFecha).reversed())
                .map(this::toVentaResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VentaDtos.CuentaPorCobrarResponse> cuentasPorCobrar(Usuario usuario, Integer idEmpresa, Integer idCliente) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> ApiException.notFound("Cliente no encontrado."));
        boolean own = java.util.Objects.equals(cliente.getIdUsuario(),usuario.getIdUsuario());
        boolean operador = rbac.resolve(usuario,idEmpresa,null).allows("sale:read");
        if (!belongsToCompany(cliente,idEmpresa)) throw ApiException.notFound("Cliente no encontrado en esta empresa.");
        if (!operador && !own) {
            throw ApiException.forbidden("No tiene permiso para consultar las cuentas por cobrar de este cliente.");
        }
        List<VentaDtos.CuentaPorCobrarResponse> result = new ArrayList<>();
        for (CuentaPorCobrar cuenta : cuentaPorCobrarRepository.findByVenta_IdCliente(idCliente)) {
            CajaSesion sesion = cajaSesionRepository.findWithCajaSucursalEmpresa(cuenta.getVenta().getIdCajaSesion())
                    .orElse(null);
            if (sesion == null || !idEmpresa.equals(sesion.getCaja().getSucursal().getEmpresa().getIdEmpresa())) {
                continue;
            }
            if (!own && !branchAllowed(usuario,idEmpresa,sesion.getCaja().getSucursal().getIdSucursal(),"sale:read")) continue;
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
            dto.setPagosReportados(pagosReportados.findByIdCxcOrderByFechaReporteDesc(cuenta.getIdCxc())
                    .stream().map(this::toPagoReportado).toList());
            result.add(dto);
        }
        return result;
    }

    /** HU-10: historial de pagos de crédito del cliente (mismas reglas de acceso que sus cuentas por cobrar). */
    @Transactional(readOnly = true)
    public List<VentaDtos.HistorialPagoResponse> historialPagos(Usuario usuario, Integer idEmpresa, Integer idCliente) {
        List<VentaDtos.HistorialPagoResponse> result = new ArrayList<>();
        for (VentaDtos.CuentaPorCobrarResponse cuenta : cuentasPorCobrar(usuario, idEmpresa, idCliente)) {
            for (VentaDtos.PagoCreditoResponse pago : cuenta.getPagosCredito()) {
                VentaDtos.HistorialPagoResponse row = new VentaDtos.HistorialPagoResponse();
                row.setIdCxc(cuenta.getIdCxc());
                row.setIdVenta(cuenta.getIdVenta());
                row.setIdPagoCredito(pago.getIdPagoCredito());
                row.setMontoPagado(pago.getMontoPagado());
                row.setFechaPago(pago.getFechaPago());
                row.setMetodoPago(pago.getMetodoPago());
                result.add(row);
            }
        }
        result.sort(java.util.Comparator.comparing(VentaDtos.HistorialPagoResponse::getFechaPago,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        return result;
    }

    private CajaSesion requireSesionAbierta(Usuario usuario, Integer idCajaSesion) {
        cajaSesionRepository.lockById(idCajaSesion).orElseThrow(() -> ApiException.notFound("Caja sesion no encontrada."));
        CajaSesion sesion = requireSesionDelUsuario(usuario, idCajaSesion);
        if (!Boolean.TRUE.equals(sesion.getCaja().getActivo())) throw ApiException.badRequest("La caja está inactiva.");
        if (!"Abierto".equalsIgnoreCase(sesion.getEstado())) {
            throw ApiException.badRequest("La caja sesion debe estar abierta.");
        }
        return sesion;
    }

    private CajaSesion requireSesionDelUsuario(Usuario usuario, Integer idCajaSesion) {
        CajaSesion sesion = cajaSesionRepository.findWithCajaSucursalEmpresa(idCajaSesion)
                .orElseThrow(() -> ApiException.notFound("Caja sesion no encontrada."));
        empresaAccess.requireSucursal(usuario,sesion.getCaja().getSucursal().getEmpresa().getIdEmpresa(),sesion.getCaja().getSucursal().getIdSucursal());
        if (!usuario.getIdUsuario().equals(sesion.getIdUsuario())) {
            throw ApiException.forbidden("La caja sesion no fue creada por el usuario actual.");
        }
        return sesion;
    }

    private void sessionPermission(Usuario user,CajaSesion session,String code) {
        var branch=session.getCaja().getSucursal();
        empresaAccess.requirePermiso(user,branch.getEmpresa().getIdEmpresa(),branch.getIdSucursal(),code);
    }
    private boolean branchAllowed(Usuario user,Integer company,Integer branch,String code) {
        return empresaAccess.canPermiso(user,company,branch,code);
    }
    private boolean esSaldoAFavor(MetodoPago metodo) {
        return metodo != null && "SALDO A FAVOR".equalsIgnoreCase(metodo.getNombre());
    }

    private boolean belongsToCompany(Cliente client,Integer company) {
        return company.equals(client.getIdEmpresa());
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
        dto.setMotivoAnulacion(venta.getMotivoAnulacion());
        dto.setTipoVentaNombre(venta.getTipoVenta() == null ? null : venta.getTipoVenta().getNombre());
        facturaRepository.findByVenta_IdVenta(venta.getIdVenta()).ifPresent(factura -> {
            dto.setIdFactura(factura.getIdFactura());
            dto.setCuf(factura.getCuf());
            dto.setNumeroFactura(factura.getNumeroFactura());
            dto.setEstadoSiat(factura.getEstadoSiat());
        });
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
                var actor=com.pos.common.security.SecurityUtils.currentUser();
                if(empresaAccess.canAccess(actor,producto.getIdEmpresa(),null,"")) {
                    var scope=rbac.resolve(actor,producto.getIdEmpresa(),null);
                    if(rbac.field(scope,"product","read","nombre")) simple.setNombre(producto.getNombre());
                    if(rbac.field(scope,"product","read","codigo_barra")) simple.setCodigoBarra(producto.getCodigoBarra());
                    if(rbac.field(scope,"product","read","unidad_medida")) simple.setUnidadMedida(producto.getUnidadMedida());
                }
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
