package com.pos.billetera.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import com.pos.billetera.service.BilleteraClient;
import com.pos.billetera.service.BilleteraService;
import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;
import com.pos.common.security.SecurityUtils;

/** Pasarela QR de la Billetera: cobros, estado, cancelacion, tarjeta virtual y webhook. */
@RestController
@RequestMapping("/api/billetera")
public class BilleteraController {

    private final BilleteraService service;
    private final BilleteraClient client;
    private final EmpresaAccess empresaAccess;

    public BilleteraController(BilleteraService service, BilleteraClient client, EmpresaAccess empresaAccess) {
        this.service = service;
        this.client = client;
        this.empresaAccess = empresaAccess;
    }

    /** Que integraciones estan activas en este servidor (sin claves, el POS usa el modo simulado). */
    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("qr_habilitado", client.qrHabilitado(), "tarjeta_habilitada", client.tarjetaHabilitada());
    }

    /** Body: { "id_empresa": 1, "id_sucursal": 1, "monto": 150.00, "descripcion": "Caja 1" } */
    @PostMapping("/cobros")
    public Map<String, Object> crear(@RequestBody Map<String, Object> body) {
        Integer idEmpresa = entero(body.get("id_empresa"), "id_empresa");
        Integer idSucursal = body.get("id_sucursal") == null ? null : entero(body.get("id_sucursal"), "id_sucursal");
        BigDecimal monto = decimal(body.get("monto"));
        Object descripcion = body.get("descripcion");
        return service.crearCobro(SecurityUtils.currentUser(), idEmpresa, idSucursal, monto,
                descripcion == null ? null : descripcion.toString());
    }

    @GetMapping("/cobros/{id}")
    public Map<String, Object> estado(@PathVariable String id) {
        return service.estado(SecurityUtils.currentUser(), id);
    }

    @PostMapping("/cobros/{id}/cancelar")
    public Map<String, Object> cancelar(@PathVariable String id) {
        service.cancelar(SecurityUtils.currentUser(), id);
        return Map.of("ok", true);
    }

    /**
     * Autoriza un cobro con la tarjeta virtual de la billetera, como una terminal ATC/Linkser.
     * Los datos de la tarjeta solo se reenvian a la billetera: no se guardan ni se registran.
     */
    @PostMapping("/tarjeta/autorizar")
    public Object autorizarTarjeta(@RequestBody Map<String, Object> body) {
        Integer idEmpresa = entero(body.get("id_empresa"), "id_empresa");
        Integer idSucursal = body.get("id_sucursal") == null ? null : entero(body.get("id_sucursal"), "id_sucursal");
        var usuario = SecurityUtils.currentUser();
        if (idSucursal == null) empresaAccess.requirePermiso(usuario, idEmpresa, "sale:create");
        else empresaAccess.requirePermiso(usuario, idEmpresa, idSucursal, "sale:create");
        return client.autorizarTarjeta(Map.of(
                "cardNumber", texto(body.get("numero")).replaceAll("\\D", ""),
                "expMonth", entero(body.get("mes"), "mes"),
                "expYear", entero(body.get("anio"), "anio"),
                "cvv", texto(body.get("cvv")),
                "holderName", texto(body.getOrDefault("titular", "")),
                "amount", decimal(body.get("monto")).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                "currency", "BOB",
                "merchantName", texto(body.getOrDefault("comercio", "POS")),
                "reference", "TJ-" + idEmpresa + "-" + System.currentTimeMillis()));
    }

    /** Webhook de la billetera: publico, protegido por la firma HMAC sobre el cuerpo crudo. */
    @PostMapping("/webhook")
    public Map<String, Object> webhook(HttpServletRequest request) throws java.io.IOException {
        String cuerpo = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        service.webhook(client.verificarWebhook(cuerpo, request.getHeader("Billetera-Signature")));
        return Map.of("received", true);
    }

    private static Integer entero(Object valor, String campo) {
        try {
            return Integer.valueOf(texto(valor).trim());
        } catch (Exception e) {
            throw ApiException.badRequest("Valor inválido para " + campo + ".");
        }
    }

    private static BigDecimal decimal(Object valor) {
        try {
            return new BigDecimal(texto(valor).trim());
        } catch (Exception e) {
            throw ApiException.badRequest("Monto inválido.");
        }
    }

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }
}
