package com.pos.inventario.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;
import com.pos.empresas.domain.Sucursal;
import com.pos.empresas.repo.SucursalRepository;
import com.pos.inventario.domain.MovimientoInventario;
import com.pos.inventario.domain.Stock;
import com.pos.inventario.domain.TipoMovimiento;
import com.pos.inventario.dto.InventarioDtos;
import com.pos.inventario.repo.MovimientoInventarioRepository;
import com.pos.inventario.repo.StockRepository;
import com.pos.inventario.repo.TipoMovimientoRepository;
import com.pos.notifications.service.NotificationService;
import com.pos.productos.domain.Producto;
import com.pos.productos.repo.ProductoRepository;
import com.pos.usuarios.domain.Usuario;

@Service
public class InventarioService {

    private final EmpresaAccess empresaAccess;
    private final TipoMovimientoRepository tipoMovimientoRepository;
    private final StockRepository stockRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final ProductoRepository productoRepository;
    private final SucursalRepository sucursalRepository;
    private final NotificationService notificationService;

    public InventarioService(
            EmpresaAccess empresaAccess,
            TipoMovimientoRepository tipoMovimientoRepository,
            StockRepository stockRepository,
            MovimientoInventarioRepository movimientoRepository,
            ProductoRepository productoRepository,
            SucursalRepository sucursalRepository,
            NotificationService notificationService) {
        this.empresaAccess = empresaAccess;
        this.tipoMovimientoRepository = tipoMovimientoRepository;
        this.stockRepository = stockRepository;
        this.movimientoRepository = movimientoRepository;
        this.productoRepository = productoRepository;
        this.sucursalRepository = sucursalRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<InventarioDtos.TipoMovimientoResponse> listarTipos() {
        return tipoMovimientoRepository.findAllByOrderByIdTipoMovimientoAsc().stream()
                .map(this::toTipo)
                .toList();
    }

    @Transactional
    public void sincronizarStocksPorProducto(Integer idProducto, Integer idEmpresa) {
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> ApiException.notFound("Producto no encontrado."));
        Integer empresaId = idEmpresa != null ? idEmpresa : producto.getIdEmpresa();
        if (empresaId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Sucursal sucursal : sucursalRepository.findByEmpresa_IdEmpresa(empresaId)) {
            stockRepository.findByProducto_IdProductoAndIdSucursal(idProducto, sucursal.getIdSucursal())
                    .orElseGet(() -> {
                        Stock stock = new Stock();
                        stock.setProducto(producto);
                        stock.setIdSucursal(sucursal.getIdSucursal());
                        stock.setCantidad(0);
                        stock.setStockMinimo(0);
                        stock.setFechaActualizacion(now);
                        return stockRepository.save(stock);
                    });
        }
    }

    @Transactional(readOnly = true)
    public List<InventarioDtos.StockResponse> listarStock(Usuario usuario, Integer idEmpresa, Integer idSucursal) {
        empresaAccess.requireSucursal(usuario, idEmpresa, idSucursal);
        List<InventarioDtos.StockResponse> result = new ArrayList<>();
        for (Stock stock : stockRepository.findByIdSucursal(idSucursal)) {
            if (stock.getProducto() != null && idEmpresa.equals(stock.getProducto().getIdEmpresa())) {
                result.add(toStock(stock));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<InventarioDtos.MovimientoListItem> listarMovimientos(
            Usuario usuario, Integer idEmpresa, Integer idSucursal, int skip, int limit) {
        empresaAccess.requireSucursal(usuario, idEmpresa, idSucursal);
        int size = Math.max(1, Math.min(limit, 100));
        int page = Math.max(0, skip) / size;
        List<MovimientoInventario> movimientos = movimientoRepository
                .findByIdSucursalOrderByFechaMovimientoDesc(idSucursal, PageRequest.of(page, size));
        List<InventarioDtos.MovimientoListItem> result = new ArrayList<>();
        for (MovimientoInventario movimiento : movimientos) {
            result.add(toListItem(movimiento));
        }
        return result;
    }

    @Transactional
    public InventarioDtos.StockResponse actualizarStock(
            Usuario usuario, Integer idEmpresa, Integer idSucursal, Integer idProducto, InventarioDtos.StockUpdate payload) {
        empresaAccess.requireSucursal(usuario, idEmpresa, idSucursal);
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> ApiException.notFound("Producto no encontrado."));
        if (!idEmpresa.equals(producto.getIdEmpresa())) {
            throw ApiException.badRequest("El producto no pertenece a la empresa indicada.");
        }
        LocalDateTime now = LocalDateTime.now();
        Stock stock = stockRepository.findByProducto_IdProductoAndIdSucursal(idProducto, idSucursal)
                .orElseGet(() -> {
                    Stock created = new Stock();
                    created.setProducto(producto);
                    created.setIdSucursal(idSucursal);
                    created.setCantidad(0);
                    created.setFechaActualizacion(now);
                    return created;
                });
        stock.setStockMinimo(payload.getStockMinimo());
        stock.setStockMaximo(payload.getStockMaximo());
        stock.setFechaActualizacion(now);
        return toStock(stockRepository.save(stock));
    }

    @Transactional
    public InventarioDtos.MovimientoResponse crearMovimiento(
            Usuario usuario,
            Integer idEmpresa,
            Integer idSucursal,
            InventarioDtos.MovimientoCreate payload) {
        empresaAccess.requireSucursal(usuario, idEmpresa, idSucursal);
        if (payload.getCantidad() == null || payload.getCantidad() <= 0) {
            throw ApiException.badRequest("La cantidad debe ser mayor a cero.");
        }
        Producto producto = productoRepository.findById(payload.getIdProducto())
                .orElseThrow(() -> ApiException.notFound("Producto no encontrado."));
        if (!idEmpresa.equals(producto.getIdEmpresa())) {
            throw ApiException.badRequest("El producto no pertenece a la empresa indicada.");
        }
        TipoMovimiento tipo = tipoMovimientoRepository.findById(payload.getIdTipoMovimiento())
                .orElseThrow(() -> ApiException.notFound("Tipo de movimiento no encontrado."));
        String direccion = (tipo.getDireccion() == null ? "" : tipo.getDireccion()).trim().toLowerCase(Locale.ROOT);
        if (!direccion.equals("entrada") && !direccion.equals("salida")) {
            throw ApiException.badRequest("La direccion del tipo de movimiento no es valida.");
        }
        LocalDateTime now = LocalDateTime.now();
        Stock stock = stockRepository.findByProducto_IdProductoAndIdSucursal(payload.getIdProducto(), idSucursal)
                .orElseGet(() -> {
                    Stock created = new Stock();
                    created.setProducto(producto);
                    created.setIdSucursal(idSucursal);
                    created.setCantidad(0);
                    created.setStockMinimo(0);
                    created.setStockMaximo(0);
                    created.setFechaActualizacion(now);
                    return stockRepository.save(created);
                });
        int nuevaCantidad = direccion.equals("entrada")
                ? stock.getCantidad() + payload.getCantidad()
                : stock.getCantidad() - payload.getCantidad();
        if (nuevaCantidad < 0) {
            throw ApiException.badRequest("Stock insuficiente para registrar la salida.");
        }
        MovimientoInventario movimiento = new MovimientoInventario();
        movimiento.setProducto(producto);
        movimiento.setTipoMovimiento(tipo);
        movimiento.setIdUsuario(usuario.getIdUsuario());
        movimiento.setIdSucursal(idSucursal);
        movimiento.setCantidad(payload.getCantidad());
        movimiento.setObservacion(payload.getObservacion());
        movimiento.setFechaMovimiento(now);
        movimiento = movimientoRepository.save(movimiento);

        stock.setCantidad(nuevaCantidad);
        stock.setFechaActualizacion(now);
        stock = stockRepository.save(stock);

        if (stock.getStockMinimo() != null && stock.getCantidad() <= stock.getStockMinimo()) {
            Sucursal sucursal = sucursalRepository.findById(idSucursal).orElse(null);
            try {
                notificationService.enviarAlerta(
                        idEmpresa,
                        "Stock bajo en \"" + (sucursal != null ? sucursal.getNombre() : idSucursal) + "\"",
                        "El producto " + producto.getNombre() + " tiene stock actual " + stock.getCantidad()
                                + " y el stock minimo es " + stock.getStockMinimo() + ".",
                        Map.of(
                                "id_producto", String.valueOf(producto.getIdProducto()),
                                "id_sucursal", String.valueOf(idSucursal),
                                "nombre_producto", producto.getNombre(),
                                "stock_actual", stock.getCantidad(),
                                "stock_minimo", stock.getStockMinimo()));
            } catch (Exception ignored) {
            }
        }
        return toMovimiento(movimiento, stock.getCantidad());
    }

    private InventarioDtos.TipoMovimientoResponse toTipo(TipoMovimiento tipo) {
        InventarioDtos.TipoMovimientoResponse dto = new InventarioDtos.TipoMovimientoResponse();
        dto.setIdTipoMovimiento(tipo.getIdTipoMovimiento());
        dto.setNombre(tipo.getNombre());
        dto.setDescripcion(tipo.getDescripcion());
        dto.setDireccion(tipo.getDireccion());
        return dto;
    }

    private InventarioDtos.StockResponse toStock(Stock stock) {
        InventarioDtos.StockResponse dto = new InventarioDtos.StockResponse();
        dto.setIdStock(stock.getIdStock());
        dto.setIdProducto(stock.getProducto().getIdProducto());
        dto.setIdSucursal(stock.getIdSucursal());
        dto.setCantidad(stock.getCantidad());
        dto.setStockMinimo(stock.getStockMinimo());
        dto.setStockMaximo(stock.getStockMaximo());
        dto.setFechaActualizacion(stock.getFechaActualizacion());
        Producto producto = stock.getProducto();
        dto.setNombreProducto(producto.getNombre());
        dto.setCodigoBarra(producto.getCodigoBarra());
        dto.setUnidadMedida(producto.getUnidadMedida() == null ? "" : producto.getUnidadMedida());
        dto.setPrecio(producto.getPrecio() == null ? 0 : producto.getPrecio().doubleValue());
        dto.setImagen(producto.getImagen());
        dto.setActivo(producto.getActivo());
        return dto;
    }

    private InventarioDtos.MovimientoResponse toMovimiento(MovimientoInventario movimiento, int stockActual) {
        InventarioDtos.MovimientoResponse dto = new InventarioDtos.MovimientoResponse();
        dto.setIdMovimientoInventario(movimiento.getIdMovimientoInventario());
        dto.setIdProducto(movimiento.getProducto().getIdProducto());
        dto.setIdTipoMovimiento(movimiento.getTipoMovimiento().getIdTipoMovimiento());
        dto.setIdUsuario(movimiento.getIdUsuario());
        dto.setIdSucursal(movimiento.getIdSucursal());
        dto.setCantidad(movimiento.getCantidad());
        dto.setObservacion(movimiento.getObservacion());
        dto.setFechaMovimiento(movimiento.getFechaMovimiento());
        dto.setStockActual(stockActual);
        dto.setTipoMovimiento(toTipo(movimiento.getTipoMovimiento()));
        return dto;
    }

    private InventarioDtos.MovimientoListItem toListItem(MovimientoInventario movimiento) {
        InventarioDtos.MovimientoListItem dto = new InventarioDtos.MovimientoListItem();
        dto.setIdMovimiento(movimiento.getIdMovimientoInventario());
        dto.setIdProducto(movimiento.getProducto().getIdProducto());
        dto.setCantidad(movimiento.getCantidad());
        dto.setObservacion(movimiento.getObservacion());
        String direccion = movimiento.getTipoMovimiento() == null ? ""
                : (movimiento.getTipoMovimiento().getDireccion() == null ? ""
                        : movimiento.getTipoMovimiento().getDireccion().trim().toUpperCase(Locale.ROOT));
        dto.setTipo(direccion);
        InventarioDtos.ProductoSimple producto = new InventarioDtos.ProductoSimple();
        producto.setIdProducto(movimiento.getProducto().getIdProducto());
        producto.setNombre(movimiento.getProducto().getNombre());
        dto.setProducto(producto);
        if (movimiento.getTipoMovimiento() != null) {
            InventarioDtos.TipoSimple tipo = new InventarioDtos.TipoSimple();
            tipo.setIdTipoMovimiento(movimiento.getTipoMovimiento().getIdTipoMovimiento());
            tipo.setNombre(movimiento.getTipoMovimiento().getNombre());
            dto.setTipoMovimiento(tipo);
        }
        return dto;
    }

    @SuppressWarnings("unused")
    private BigDecimal unused() {
        return BigDecimal.ZERO;
    }
}
