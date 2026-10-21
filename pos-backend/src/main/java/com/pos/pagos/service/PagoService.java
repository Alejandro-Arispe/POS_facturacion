package com.pos.pagos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;
import com.pos.empresas.domain.Empresa;
import com.pos.empresas.domain.HistorialSuscripcion;
import com.pos.empresas.domain.Plan;
import com.pos.empresas.repo.HistorialSuscripcionRepository;
import com.pos.empresas.repo.PlanRepository;
import com.pos.pagos.dto.PagoDtos;
import com.pos.usuarios.domain.Usuario;

@Service
public class PagoService {

    private final EmpresaAccess empresaAccess;
    private final PlanRepository planRepository;
    private final HistorialSuscripcionRepository historialRepository;
    private final String stripeSecret;
    private final String webhookSecret;
    private final String frontendBaseUrl;
    private final ObjectMapper objectMapper;
    private final RestClient stripeClient = RestClient.builder().baseUrl("https://api.stripe.com").build();

    public PagoService(
            EmpresaAccess empresaAccess,
            PlanRepository planRepository,
            HistorialSuscripcionRepository historialRepository,
            ObjectMapper objectMapper,
            @Value("${app.stripe.secret-key:}") String stripeSecret,
            @Value("${app.stripe.webhook-secret:}") String webhookSecret,
            @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.empresaAccess = empresaAccess;
        this.planRepository = planRepository;
        this.historialRepository = historialRepository;
        this.objectMapper = objectMapper;
        this.stripeSecret = stripeSecret == null ? "" : stripeSecret;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public PagoDtos.CheckoutResponse checkout(Usuario usuario, PagoDtos.CheckoutRequest request) {
        Empresa empresa = empresaAccess.requireEmpresa(usuario, request.getIdEmpresa());
        Plan plan = planRepository.findById(request.getIdPlan())
                .orElseThrow(() -> ApiException.notFound("Plan no encontrado."));
        if (plan.getPrecio() == null || plan.getPrecio().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("El plan no tiene precio configurado.");
        }
        PagoDtos.CheckoutResponse response = new PagoDtos.CheckoutResponse();
        response.setPlanNombre(plan.getNombre());
        response.setMonto(plan.getPrecio().toPlainString());
        response.setMoneda("usd");
        if (stripeSecret.isBlank()) {
            String sessionId = "stub_" + UUID.randomUUID();
            guardarHistorial(empresa, plan, sessionId, null, "paid", "activo");
            response.setSessionId(sessionId);
            response.setCheckoutUrl(frontendBaseUrl + "/administrator/payment/success?session_id=" + sessionId);
            return response;
        }
        JsonNode session = crearSesionStripe(empresa, plan);
        String sessionId = session.path("id").asText("");
        String url = session.path("url").asText("");
        if (sessionId.isBlank() || url.isBlank()) {
            throw ApiException.badRequest("Stripe no devolvió una sesión de pago.");
        }
        guardarHistorial(empresa, plan, sessionId, null, "unpaid", "pendiente");
        response.setSessionId(sessionId);
        response.setCheckoutUrl(url);
        return response;
    }

    @Transactional
    public PagoDtos.ConfirmarResponse confirmar(Usuario usuario, String sessionId) {
        empresaAccess.requireUsuarioActivo(usuario);
        HistorialSuscripcion historial = historialRepository.findByStripeSessionId(sessionId).orElse(null);
        if (historial == null) {
            throw ApiException.badRequest("session_id inválido o no encontrado.");
        }
        if (!sessionId.startsWith("stub_") && "pendiente".equalsIgnoreCase(historial.getEstado())) {
            activarSiPagada(historial, consultarSesionStripe(sessionId));
        }
        PagoDtos.ConfirmarResponse response = new PagoDtos.ConfirmarResponse();
        response.setMensaje("activo".equalsIgnoreCase(historial.getEstado())
                ? "Suscripción activa. Ya puedes entrar a la empresa."
                : "El pago todavía no está confirmado.");
        response.setSuscripcion(toSuscripcion(historial));
        return response;
    }

    public Map<String, Object> webhook(String payload, String signature) {
        if (stripeSecret.isBlank()) {
            return Map.of("received", true, "modo", "local");
        }
        if (webhookSecret.isBlank() || !firmaValida(payload, signature)) {
            throw ApiException.badRequest("Firma de webhook inválida.");
        }
        try {
            JsonNode event = objectMapper.readTree(payload);
            if ("checkout.session.completed".equals(event.path("type").asText())) {
                JsonNode session = event.path("data").path("object");
                String sessionId = session.path("id").asText("");
                historialRepository.findByStripeSessionId(sessionId).ifPresent(historial -> activarSiPagada(historial, session));
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("No se pudo leer el evento de Stripe.");
        }
        return Map.of("received", true);
    }

    private JsonNode crearSesionStripe(Empresa empresa, Plan plan) {
        int centavos = plan.getPrecio().movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
        String success = url(frontendBaseUrl + "/administrator/payment/success?session_id={CHECKOUT_SESSION_ID}");
        String cancel = url(frontendBaseUrl + "/administrator/payment/cancel");
        String nombre = url(plan.getNombre() == null ? "Plan" : plan.getNombre());
        String form = "mode=payment"
                + "&success_url=" + success
                + "&cancel_url=" + cancel
                + "&client_reference_id=" + empresa.getIdEmpresa()
                + "&metadata[id_empresa]=" + empresa.getIdEmpresa()
                + "&metadata[id_plan]=" + plan.getIdPlan()
                + "&line_items[0][quantity]=1"
                + "&line_items[0][price_data][currency]=usd"
                + "&line_items[0][price_data][unit_amount]=" + centavos
                + "&line_items[0][price_data][product_data][name]=" + nombre;
        return stripe(form, "/v1/checkout/sessions");
    }

    private JsonNode consultarSesionStripe(String sessionId) {
        try {
            String body = stripeClient.get()
                    .uri("/v1/checkout/sessions/" + sessionId)
                    .header("Authorization", "Bearer " + stripeSecret)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (RestClientResponseException ex) {
            throw ApiException.badRequest("Stripe no confirmó la sesión de pago.");
        } catch (Exception ex) {
            throw ApiException.badRequest("No se pudo consultar la sesión de pago.");
        }
    }

    private JsonNode stripe(String form, String path) {
        try {
            String body = stripeClient.post()
                    .uri(path)
                    .header("Authorization", "Bearer " + stripeSecret)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (RestClientResponseException ex) {
            throw ApiException.badRequest("Stripe rechazó el cobro. Revisa la clave y el precio del plan.");
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("No se pudo abrir el cobro en Stripe.");
        }
    }

    private void guardarHistorial(Empresa empresa, Plan plan, String sessionId, String paymentIntent, String paymentStatus, String estado) {
        HistorialSuscripcion historial = new HistorialSuscripcion();
        historial.setPlan(plan);
        historial.setEmpresa(empresa);
        historial.setFechaInicio(LocalDate.now());
        historial.setFechaFin(LocalDate.now().plusDays(30));
        historial.setEstado(estado);
        historial.setStripeSessionId(sessionId);
        historial.setStripePaymentIntentId(paymentIntent);
        historial.setStripePaymentStatus(paymentStatus);
        historialRepository.save(historial);
    }

    private void activarSiPagada(HistorialSuscripcion historial, JsonNode session) {
        String paymentStatus = session.path("payment_status").asText("");
        if (!"paid".equalsIgnoreCase(paymentStatus)) {
            return;
        }
        historial.setEstado("activo");
        historial.setStripePaymentStatus("paid");
        String intent = session.path("payment_intent").asText("");
        if (!intent.isBlank()) {
            historial.setStripePaymentIntentId(intent);
        }
        historial.setFechaInicio(LocalDate.now());
        historial.setFechaFin(LocalDate.now().plusDays(30));
        historialRepository.save(historial);
    }

    private boolean firmaValida(String payload, String signature) {
        if (payload == null || signature == null || signature.isBlank()) {
            return false;
        }
        String timestamp = null;
        String v1 = null;
        for (String parte : signature.split(",")) {
            String[] kv = parte.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if ("t".equals(kv[0])) {
                timestamp = kv[1];
            } else if ("v1".equals(kv[0])) {
                v1 = kv[1];
            }
        }
        if (timestamp == null || v1 == null) {
            return false;
        }
        try {
            long segundos = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - segundos) > 300) {
                return false;
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String esperado = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), v1.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private PagoDtos.SuscripcionResponse toSuscripcion(HistorialSuscripcion historial) {
        PagoDtos.SuscripcionResponse dto = new PagoDtos.SuscripcionResponse();
        dto.setIdHistorialSuscripcion(historial.getIdHistorialSuscripcion());
        dto.setIdEmpresa(historial.getEmpresa().getIdEmpresa());
        dto.setIdPlan(historial.getPlan().getIdPlan());
        dto.setFechaInicio(historial.getFechaInicio());
        dto.setFechaFin(historial.getFechaFin());
        dto.setEstado(historial.getEstado());
        dto.setStripeSessionId(historial.getStripeSessionId());
        dto.setStripePaymentIntentId(historial.getStripePaymentIntentId());
        dto.setStripePaymentStatus(historial.getStripePaymentStatus());
        return dto;
    }
}
