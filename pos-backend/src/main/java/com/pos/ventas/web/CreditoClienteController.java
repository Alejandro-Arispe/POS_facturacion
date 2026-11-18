package com.pos.ventas.web;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pos.common.security.SecurityUtils;
import com.pos.ventas.dto.VentaDtos;
import com.pos.ventas.service.VentaService;

/** HU-10: el cliente autenticado informa pagos de sus propios créditos (quedan PENDIENTES de confirmar en caja). */
@RestController
@RequestMapping("/api/creditos")
public class CreditoClienteController {
    private final VentaService ventaService;

    public CreditoClienteController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    @PostMapping("/me/{idCxc}/pagar")
    public VentaDtos.PagoReportadoResponse pagar(@PathVariable Integer idCxc,
            @RequestBody VentaDtos.PagoReportadoCreate payload) {
        return ventaService.reportarPago(SecurityUtils.currentUser(), idCxc, payload);
    }
}
