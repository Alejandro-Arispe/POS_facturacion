package com.pos.ventas.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.clientes.domain.Cliente;
import com.pos.clientes.repo.ClienteRepository;
import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;
import com.pos.inventario.domain.Stock;
import com.pos.inventario.repo.StockRepository;
import com.pos.productos.domain.Producto;
import com.pos.productos.repo.ProductoRepository;
import com.pos.usuarios.domain.Usuario;
import com.pos.ventas.domain.PedidoCliente;
import com.pos.ventas.domain.PedidoClienteDetalle;
import com.pos.ventas.dto.PedidoDtos;
import com.pos.ventas.repo.PedidoClienteDetalleRepository;
import com.pos.ventas.repo.PedidoClienteRepository;

@Service
public class PedidoService {

    private static final Map<String, Set<String>> TRANSICIONES = Map.of(
            "enviado", Set.of("en_revision", "cancelado"),
            "en_revision", Set.of("confirmado", "rechazado", "cancelado"),
            "confirmado", Set.of("en_preparacion"),
            "en_preparacion", Set.of("listo_para_recoger"),
            "listo_para_recoger", Set.of("convertido_en_venta"),
            "convertido_en_venta", Set.of("entregado"));

    private final EmpresaAccess empresaAccess;
    private final PedidoClienteRepository pedidoRepository;
    private final PedidoClienteDetalleRepository detalleRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final StockRepository stockRepository;

    public PedidoService(
            EmpresaAccess empresaAccess,
            PedidoClienteRepository pedidoRepository,
            PedidoClienteDetalleRepository detalleRepository,
            ClienteRepository clienteRepository,
            ProductoRepository productoRepository,
            StockRepository stockRepository) {
        this.empresaAccess = empresaAccess;
        this.pedidoRepository = pedidoRepository;
        this.detalleRepository = detalleRepository;
        this.clienteRepository = clienteRepository;
        this.productoRepository = productoRepository;
        this.stockRepository = stockRepository;
    }

    @Transactional
    public PedidoDtos.PedidoResponse crear(Usuario usuario, Integer idEmpresa, PedidoDtos.PedidoCreate payload) {
        empresaAccess.requireSucursal(usuario, idEmpresa, payload.getIdSucursal());
        Cliente cliente = clienteRepository.findFirstByIdUsuarioOrderByIdClienteAsc(usuario.getIdUsuario())
                .orElseThrow(() -> ApiException.badRequest("No se encontró perfil de cliente para este usuario."));
        BigDecimal descuentoPct = BigDecimal.ZERO;
        if (cliente.getCategoriaCliente() != null && cliente.getCategoriaCliente().getDescuentoBase() != null) {
            descuentoPct = cliente.getCategoriaCliente().getDescuentoBase()
                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        }
        PedidoCliente pedido = new PedidoCliente();
        pedido.setIdEmpresa(idEmpresa);
        pedido.setIdSucursal(payload.getIdSucursal());
        pedido.setIdCliente(cliente.getIdCliente());
        pedido.setEstado("enviado");
        pedido.setObservacionCliente(payload.getObservacionCliente());
        pedido.setFechaCreacion(LocalDateTime.now());
        pedido.setSubtotalEstimado(BigDecimal.ZERO);
        pedido.setDescuentoEstimado(BigDecimal.ZERO);
        pedido.setTotalEstimado(BigDecimal.ZERO);
        pedido = pedidoRepository.save(pedido);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal descuento = BigDecimal.ZERO;
        if (payload.getDetalles() == null || payload.getDetalles().isEmpty()) {
            throw ApiException.badRequest("El pedido debe tener detalles.");
        }
        for (PedidoDtos.DetalleCreate item : payload.getDetalles()) {
            Producto producto = productoRepository.findById(item.getIdProducto())
                    .orElseThrow(() -> ApiException.badRequest("Producto " + item.getIdProducto() + " no es valido o no esta activo."));
            if (!idEmpresa.equals(producto.getIdEmpresa()) || !Boolean.TRUE.equals(producto.getActivo())) {
                throw ApiException.badRequest("Producto " + item.getIdProducto() + " no es valido o no esta activo.");
            }
            if (item.getCantidad() == null || item.getCantidad() <= 0) {
                throw ApiException.badRequest("Cantidad para producto " + item.getIdProducto() + " debe ser mayor a 0.");
            }
            int disponible = stockRepository.findByProducto_IdProductoAndIdSucursal(item.getIdProducto(), payload.getIdSucursal())
                    .map(Stock::getCantidad)
                    .orElse(0);
            if (item.getCantidad() > disponible) {
                throw new ApiException(HttpStatus.BAD_REQUEST, Map.of(
                        "codigo", "STOCK_INSUFICIENTE",
                        "producto_id", item.getIdProducto(),
                        "producto", producto.getNombre(),
                        "solicitado", item.getCantidad(),
                        "disponible", disponible),
                        "Stock insuficiente");
            }
            BigDecimal precio = producto.getPrecio() == null ? BigDecimal.ZERO : producto.getPrecio();
            BigDecimal subtotalItem = precio.multiply(BigDecimal.valueOf(item.getCantidad()));
            BigDecimal descItem = subtotalItem.multiply(descuentoPct);
            PedidoClienteDetalle detalle = new PedidoClienteDetalle();
            detalle.setPedido(pedido);
            detalle.setIdProducto(item.getIdProducto());
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitarioEstimado(precio);
            detalle.setDescuentoEstimado(descItem);
            detalle.setSubtotalEstimado(subtotalItem.subtract(descItem));
            detalleRepository.save(detalle);
            subtotal = subtotal.add(subtotalItem);
            descuento = descuento.add(descItem);
        }
        pedido.setSubtotalEstimado(subtotal);
        pedido.setDescuentoEstimado(descuento);
        pedido.setTotalEstimado(subtotal.subtract(descuento));
        return toResponse(pedidoRepository.save(pedido));
    }

    @Transactional(readOnly = true)
    public List<PedidoDtos.PedidoResponse> misPedidos(Usuario usuario) {
        return clienteRepository.findFirstByIdUsuarioOrderByIdClienteAsc(usuario.getIdUsuario())
                .map(cliente -> pedidoRepository.findByIdClienteOrderByFechaCreacionDesc(cliente.getIdCliente())
                        .stream().map(this::toResponse).toList())
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public PedidoDtos.PedidoResponse miPedido(Usuario usuario, Integer idPedido) {
        Cliente cliente = clienteRepository.findFirstByIdUsuarioOrderByIdClienteAsc(usuario.getIdUsuario())
                .orElseThrow(() -> ApiException.notFound("No se encontro perfil de cliente."));
        PedidoCliente pedido = pedidoRepository.findById(idPedido)
                .orElseThrow(() -> ApiException.notFound("Pedido no encontrado o no pertenece a este cliente."));
        if (!cliente.getIdCliente().equals(pedido.getIdCliente())) {
            throw ApiException.notFound("Pedido no encontrado o no pertenece a este cliente.");
        }
        return toResponse(pedido);
    }

    @Transactional
    public PedidoDtos.PedidoResponse cancelar(Usuario usuario, Integer idPedido) {
        PedidoDtos.PedidoResponse actual = miPedido(usuario, idPedido);
        PedidoCliente pedido = pedidoRepository.findById(actual.getIdPedido()).orElseThrow();
        validarTransicion(pedido.getEstado(), "cancelado");
        pedido.setEstado("cancelado");
        pedido.setFechaCancelacion(LocalDateTime.now());
        return toResponse(pedidoRepository.save(pedido));
    }

    @Transactional(readOnly = true)
    public List<PedidoDtos.PedidoResponse> pedidosEmpresa(
            Usuario usuario, Integer idEmpresa, Integer idSucursal, String estado) {
        empresaAccess.requirePermiso(usuario, idEmpresa, "PEDIDO_VER");
        List<PedidoCliente> pedidos;
        if (idSucursal != null && estado != null) {
            pedidos = pedidoRepository.findByIdEmpresaAndIdSucursalAndEstado(idEmpresa, idSucursal, estado);
        } else if (idSucursal != null) {
            pedidos = pedidoRepository.findByIdEmpresaAndIdSucursalOrderByFechaCreacionDesc(idEmpresa, idSucursal);
        } else if (estado != null) {
            pedidos = pedidoRepository.findByIdEmpresaAndEstadoOrderByFechaCreacionDesc(idEmpresa, estado);
        } else {
            pedidos = pedidoRepository.findByIdEmpresaOrderByFechaCreacionDesc(idEmpresa);
        }
        return pedidos.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PedidoDtos.PedidoResponse pedidoEmpresa(Usuario usuario, Integer idEmpresa, Integer idPedido) {
        empresaAccess.requirePermiso(usuario, idEmpresa, "PEDIDO_VER");
        PedidoCliente pedido = pedidoRepository.findById(idPedido)
                .orElseThrow(() -> ApiException.notFound("Pedido no encontrado."));
        if (!idEmpresa.equals(pedido.getIdEmpresa())) {
            throw ApiException.notFound("Pedido no encontrado.");
        }
        return toResponse(pedido);
    }

    @Transactional
    public PedidoDtos.PedidoResponse actualizarEstado(
            Usuario usuario, Integer idEmpresa, Integer idPedido, PedidoDtos.EstadoUpdate payload) {
        empresaAccess.requirePermiso(usuario, idEmpresa, "PEDIDO_GESTIONAR");
        PedidoCliente pedido = pedidoRepository.findById(idPedido)
                .orElseThrow(() -> ApiException.notFound("Pedido no encontrado."));
        if (!idEmpresa.equals(pedido.getIdEmpresa())) {
            throw ApiException.notFound("Pedido no encontrado.");
        }
        if ("convertido_en_venta".equals(payload.getEstado()) && pedido.getIdVenta() != null) {
            throw ApiException.badRequest("El pedido ya fue convertido en venta.");
        }
        validarTransicion(pedido.getEstado(), payload.getEstado());
        pedido.setEstado(payload.getEstado());
        if (payload.getObservacionEmpresa() != null) {
            pedido.setObservacionEmpresa(payload.getObservacionEmpresa());
        }
        LocalDateTime now = LocalDateTime.now();
        switch (payload.getEstado()) {
            case "confirmado" -> pedido.setFechaConfirmacion(now);
            case "en_preparacion" -> pedido.setFechaPreparacion(now);
            case "listo_para_recoger" -> pedido.setFechaListo(now);
            case "cancelado", "rechazado" -> pedido.setFechaCancelacion(now);
            default -> {
            }
        }
        return toResponse(pedidoRepository.save(pedido));
    }

    private void validarTransicion(String actual, String nuevo) {
        Set<String> permitidos = TRANSICIONES.getOrDefault(actual, Set.of());
        if (!permitidos.contains(nuevo)) {
            throw ApiException.badRequest("Transicion de estado no valida de '" + actual + "' a '" + nuevo + "'.");
        }
    }

    private PedidoDtos.PedidoResponse toResponse(PedidoCliente pedido) {
        PedidoDtos.PedidoResponse dto = new PedidoDtos.PedidoResponse();
        dto.setIdPedido(pedido.getIdPedido());
        dto.setIdEmpresa(pedido.getIdEmpresa());
        dto.setIdSucursal(pedido.getIdSucursal());
        dto.setIdCliente(pedido.getIdCliente());
        dto.setIdVenta(pedido.getIdVenta());
        dto.setEstado(pedido.getEstado());
        dto.setSubtotalEstimado(pedido.getSubtotalEstimado());
        dto.setDescuentoEstimado(pedido.getDescuentoEstimado());
        dto.setTotalEstimado(pedido.getTotalEstimado());
        dto.setObservacionCliente(pedido.getObservacionCliente());
        dto.setObservacionEmpresa(pedido.getObservacionEmpresa());
        dto.setFechaCreacion(pedido.getFechaCreacion());
        dto.setFechaConfirmacion(pedido.getFechaConfirmacion());
        dto.setFechaPreparacion(pedido.getFechaPreparacion());
        dto.setFechaListo(pedido.getFechaListo());
        dto.setFechaCancelacion(pedido.getFechaCancelacion());
        List<PedidoDtos.DetalleResponse> detalles = new ArrayList<>();
        for (PedidoClienteDetalle detalle : detalleRepository.findByPedido_IdPedido(pedido.getIdPedido())) {
            PedidoDtos.DetalleResponse item = new PedidoDtos.DetalleResponse();
            item.setIdDetalle(detalle.getIdDetalle());
            item.setIdPedido(pedido.getIdPedido());
            item.setIdProducto(detalle.getIdProducto());
            item.setCantidad(detalle.getCantidad());
            item.setPrecioUnitarioEstimado(detalle.getPrecioUnitarioEstimado());
            item.setDescuentoEstimado(detalle.getDescuentoEstimado());
            item.setSubtotalEstimado(detalle.getSubtotalEstimado());
            detalles.add(item);
        }
        dto.setDetalles(detalles);
        return dto;
    }
}
