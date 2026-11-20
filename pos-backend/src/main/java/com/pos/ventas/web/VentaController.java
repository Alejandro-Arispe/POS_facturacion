package com.pos.ventas.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pos.common.security.SecurityUtils;
import com.pos.ventas.dto.VentaDtos;
import com.pos.ventas.service.PostVentaService;
import com.pos.ventas.service.VentaService;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;
    private final PostVentaService postVentaService;

    public VentaController(VentaService ventaService, PostVentaService postVentaService) {
        this.ventaService = ventaService;
        this.postVentaService = postVentaService;
    }

    /** HU-14: anula la venta completa (motivo obligatorio). */
    @PostMapping("/{idVenta}/anular")
    public java.util.Map<String, Object> anular(@PathVariable Integer idVenta, @RequestBody VentaDtos.AnulacionCreate datos) {
        return postVentaService.anular(SecurityUtils.currentUser(), idVenta, datos.getMotivo(), datos.getCodigoMotivoSin());
    }

    /** HU-15: devolucion parcial o total con reembolso en efectivo o saldo a favor. */
    @PostMapping("/{idVenta}/devoluciones")
    public java.util.Map<String, Object> devolver(@PathVariable Integer idVenta, @RequestBody VentaDtos.DevolucionCreate datos) {
        List<PostVentaService.LineaDevolucion> lineas = datos.getItems() == null ? List.of()
                : datos.getItems().stream().map(i -> new PostVentaService.LineaDevolucion(i.getIdProducto(), i.getCantidad())).toList();
        return postVentaService.devolver(SecurityUtils.currentUser(), idVenta, lineas, datos.getTipoReembolso(), datos.getMotivo());
    }

    @GetMapping("/{idVenta}/devoluciones")
    public java.util.Map<String, Object> devoluciones(@PathVariable Integer idVenta) {
        return postVentaService.resumenDevoluciones(SecurityUtils.currentUser(), idVenta);
    }

    /** HU-11: compras del cliente autenticado. */
    @GetMapping("/me")
    public List<VentaDtos.VentaResponse> misVentas() {
        return ventaService.misVentas(SecurityUtils.currentUser());
    }

    @GetMapping("/tipos-venta")
    public List<VentaDtos.CatalogoItem> tiposVenta() {
        return ventaService.listarTiposVenta();
    }

    @GetMapping("/metodos-pago")
    public List<VentaDtos.CatalogoItem> metodosPago(
            @RequestParam(name = "incluir_cheque", defaultValue = "false") boolean incluirCheque) {
        return ventaService.listarMetodosPago(incluirCheque);
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
