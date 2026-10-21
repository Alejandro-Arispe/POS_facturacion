package com.pos.ventas.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pos.common.security.SecurityUtils;
import com.pos.ventas.dto.VentaDtos;
import com.pos.ventas.service.VentaService;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    @GetMapping("/tipos-venta")
    public List<VentaDtos.CatalogoItem> tiposVenta() {
        return ventaService.listarTiposVenta();
    }

    @GetMapping("/metodos-pago")
    public List<VentaDtos.CatalogoItem> metodosPago() {
        return ventaService.listarMetodosPago();
    }

    @PostMapping("/sesiones/{idCajaSesion}/ventas")
    public VentaDtos.VentaResponse crear(
            @PathVariable Integer idCajaSesion, @RequestBody VentaDtos.VentaCreate datos) {
        return ventaService.crearVenta(SecurityUtils.currentUser(), idCajaSesion, datos);
    }

    @GetMapping("/sesiones/{idCajaSesion}/ventas")
    public List<VentaDtos.VentaResponse> historial(@PathVariable Integer idCajaSesion) {
        return ventaService.historial(SecurityUtils.currentUser(), idCajaSesion);
    }

    @PostMapping("/sesiones/{idCajaSesion}/pagos-credito")
    public VentaDtos.CobroResponse pagoCredito(
            @PathVariable Integer idCajaSesion, @RequestBody VentaDtos.CobroCreate datos) {
        return ventaService.registrarPagoCredito(SecurityUtils.currentUser(), idCajaSesion, datos);
    }
}
