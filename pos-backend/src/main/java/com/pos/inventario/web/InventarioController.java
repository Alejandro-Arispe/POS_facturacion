package com.pos.inventario.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pos.common.security.SecurityUtils;
import com.pos.inventario.dto.InventarioDtos;
import com.pos.inventario.service.InventarioService;

@RestController
@RequestMapping("/api/inventario")
public class InventarioController {

    private final InventarioService inventarioService;

    public InventarioController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @GetMapping("/tipos-movimiento")
    public List<InventarioDtos.TipoMovimientoResponse> tipos() {
        return inventarioService.listarTipos();
    }

    @PostMapping("/empresas/{idEmpresa}/sucursales/{idSucursal}/movimientos")
    public InventarioDtos.MovimientoResponse crear(
            @PathVariable Integer idEmpresa,
            @PathVariable Integer idSucursal,
            @RequestBody InventarioDtos.MovimientoCreate datos) {
        return inventarioService.crearMovimiento(SecurityUtils.currentUser(), idEmpresa, idSucursal, datos);
    }

    @GetMapping("/empresas/{idEmpresa}/sucursales/{idSucursal}/stock")
    public List<InventarioDtos.StockResponse> stock(
            @PathVariable Integer idEmpresa, @PathVariable Integer idSucursal) {
        return inventarioService.listarStock(SecurityUtils.currentUser(), idEmpresa, idSucursal);
    }

    @GetMapping("/empresas/{idEmpresa}/sucursales/{idSucursal}/movimientos")
    public List<InventarioDtos.MovimientoListItem> movimientos(
            @PathVariable Integer idEmpresa,
            @PathVariable Integer idSucursal,
            @RequestParam(name = "skip", defaultValue = "0") int skip,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return inventarioService.listarMovimientos(SecurityUtils.currentUser(), idEmpresa, idSucursal, skip, limit);
    }

    @PutMapping("/empresas/{idEmpresa}/sucursales/{idSucursal}/stock/{idProducto}")
    public InventarioDtos.StockResponse actualizarStock(
            @PathVariable Integer idEmpresa,
            @PathVariable Integer idSucursal,
            @PathVariable Integer idProducto,
            @RequestBody InventarioDtos.StockUpdate datos) {
        return inventarioService.actualizarStock(SecurityUtils.currentUser(), idEmpresa, idSucursal, idProducto, datos);
    }
}
