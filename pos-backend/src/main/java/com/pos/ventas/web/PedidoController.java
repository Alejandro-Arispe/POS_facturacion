package com.pos.ventas.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pos.common.security.SecurityUtils;
import com.pos.ventas.dto.PedidoDtos;
import com.pos.ventas.service.PedidoService;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping("/me")
    public List<PedidoDtos.PedidoResponse> misPedidos() {
        return pedidoService.misPedidos(SecurityUtils.currentUser());
    }

    @GetMapping("/me/{idPedido}")
    public PedidoDtos.PedidoResponse miPedido(@PathVariable Integer idPedido) {
        return pedidoService.miPedido(SecurityUtils.currentUser(), idPedido);
    }

    @PatchMapping("/me/{idPedido}/cancelar")
    public PedidoDtos.PedidoResponse cancelar(@PathVariable Integer idPedido) {
        return pedidoService.cancelar(SecurityUtils.currentUser(), idPedido);
    }

    @PostMapping("/empresas/{idEmpresa}")
    public PedidoDtos.PedidoResponse crear(
            @PathVariable Integer idEmpresa, @RequestBody PedidoDtos.PedidoCreate payload) {
        return pedidoService.crear(SecurityUtils.currentUser(), idEmpresa, payload);
    }

    @GetMapping("/empresas/{idEmpresa}")
    public List<PedidoDtos.PedidoResponse> listarEmpresa(
            @PathVariable Integer idEmpresa,
            @RequestParam(name = "sucursal_id", required = false) Integer sucursalId,
            @RequestParam(name = "estado", required = false) String estado) {
        return pedidoService.pedidosEmpresa(SecurityUtils.currentUser(), idEmpresa, sucursalId, estado);
    }

    @GetMapping("/empresas/{idEmpresa}/{idPedido}")
    public PedidoDtos.PedidoResponse detalleEmpresa(
            @PathVariable Integer idEmpresa, @PathVariable Integer idPedido) {
        return pedidoService.pedidoEmpresa(SecurityUtils.currentUser(), idEmpresa, idPedido);
    }

    @PatchMapping("/empresas/{idEmpresa}/{idPedido}/estado")
    public PedidoDtos.PedidoResponse estado(
            @PathVariable Integer idEmpresa,
            @PathVariable Integer idPedido,
            @RequestBody PedidoDtos.EstadoUpdate payload) {
        return pedidoService.actualizarEstado(SecurityUtils.currentUser(), idEmpresa, idPedido, payload);
    }
}
