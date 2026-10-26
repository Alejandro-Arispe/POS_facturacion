package com.pos.pagos.web;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import com.pos.common.security.SecurityUtils;
import com.pos.pagos.dto.PagoDtos;
import com.pos.pagos.service.PagoService;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService pagoService;
    private final com.pos.pagos.service.CompraPlanService compras;

    public PagoController(PagoService pagoService, com.pos.pagos.service.CompraPlanService compras) {
        this.pagoService = pagoService;
        this.compras = compras;
    }

    @GetMapping("/planes-disponibles")
    public java.util.List<PagoDtos.CompraResponse> disponibles() { return compras.disponibles(SecurityUtils.currentUser()); }

    @PostMapping("/checkout")
    public PagoDtos.CheckoutResponse checkout(@RequestBody PagoDtos.CheckoutRequest request) {
        return pagoService.checkout(SecurityUtils.currentUser(), request);
    }

    @PostMapping("/confirmar")
    public PagoDtos.ConfirmarResponse confirmar(@RequestBody PagoDtos.ConfirmarRequest request) {
        return pagoService.confirmar(SecurityUtils.currentUser(), request.getSessionId());
    }

    @PostMapping("/webhook")
    public Map<String, Object> webhook(HttpServletRequest request) throws java.io.IOException {
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return pagoService.webhook(body, request.getHeader("Stripe-Signature"));
    }
}
