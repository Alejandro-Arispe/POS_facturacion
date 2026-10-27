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
    private final boolean testOnly;
    private final ObjectMapper objectMapper;
    private final RestClient stripeClient;
    private final com.pos.pagos.repo.CompraPlanRepository compras;
    private final CompraPlanService compraService;
    private final CheckoutReturnUrls returnUrls;

    public PagoService(
            EmpresaAccess empresaAccess,
            PlanRepository planRepository,
            HistorialSuscripcionRepository historialRepository,
            ObjectMapper objectMapper,
            @Value("${app.stripe.secret-key:}") String stripeSecret,
            @Value("${app.stripe.webhook-secret:}") String webhookSecret,
            @Value("${app.stripe.test-only:true}") boolean testOnly,
            @Value("${app.stripe.api-base-url:https://api.stripe.com}") String stripeApiBaseUrl,
            com.pos.pagos.repo.CompraPlanRepository compras, CompraPlanService compraService, CheckoutReturnUrls returnUrls) {
        this.empresaAccess = empresaAccess;
        this.planRepository = planRepository;
        this.historialRepository = historialRepository;
        this.objectMapper = objectMapper;
        this.stripeSecret = stripeSecret == null ? "" : stripeSecret;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret;
        this.testOnly = testOnly;
        this.stripeClient = RestClient.builder().baseUrl(stripeApiBaseUrl).build();
        this.compras = compras; this.compraService = compraService; this.returnUrls = returnUrls;
    }

    @Transactional
    public PagoDtos.CheckoutResponse checkout(Usuario usuario, PagoDtos.CheckoutRequest request) {
        empresaAccess.requireUsuarioActivo(usuario);
        String origin = returnUrls.origin(request.getFrontendOrigin());
        if (request.getIdEmpresa() == null) return checkoutCuenta(usuario, request.getIdPlan(), origin);
        Empresa empresa = empresaAccess.requireEmpresaSinPlan(usuario, request.getIdEmpresa());
        entityManager.lock(empresa, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        requireAdministrator(usuario, empresa.getIdEmpresa());
        Plan plan = planRepository.findById(request.getIdPlan())
                .orElseThrow(() -> ApiException.notFound("Plan no encontrado."));
        if (plan.getPrecio() == null || plan.getPrecio().compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("El plan no tiene precio configurado.");
        }
        PagoDtos.CheckoutResponse response = new PagoDtos.CheckoutResponse();
        response.setPlanNombre(plan.getNombre());
        response.setMonto(plan.getPrecio().toPlainString());
        response.setMoneda("bob");
        if (plan.getPrecio().signum() == 0) {
            String sessionId = "free_" + UUID.randomUUID();
            guardarHistorial(empresa, plan, sessionId, null, "paid", "activo");
            response.setSessionId(sessionId);
            response.setCheckoutUrl(origin + "/payment/success?session_id=" + sessionId);
            return response;
        }
        requireTestStripe();
        JsonNode session = crearSesionStripe(plan, String.valueOf(empresa.getIdEmpresa()),
                "&metadata[id_empresa]=" + empresa.getIdEmpresa(), origin,
                "empresa=" + empresa.getIdEmpresa() + "&plan=" + plan.getIdPlan());
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

    private PagoDtos.CheckoutResponse checkoutCuenta(Usuario usuario, Integer planId, String origin) {
        entityManager.find(Usuario.class, usuario.getIdUsuario(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        Plan plan = planRepository.findById(planId).orElseThrow(() -> ApiException.notFound("Plan no encontrado."));
        if (plan.getPrecio() == null || plan.getPrecio().signum() < 0) throw ApiException.badRequest("El plan no tiene precio configurado.");
        var response = new PagoDtos.CheckoutResponse();
        response.setPlanNombre(plan.getNombre()); response.setMonto(plan.getPrecio().toPlainString()); response.setMoneda("bob");
        var disponible = compras.findByUsuario_IdUsuarioAndEmpresaIsNullAndEstadoOrderByIdCompraPlanAsc(usuario.getIdUsuario(), "pagado")
                .stream().filter(c -> c.getPlan().getIdPlan().equals(planId)).findFirst();
        if (disponible.isPresent()) {
            response.setSessionId(disponible.get().getStripeSessionId());
            response.setCheckoutUrl(origin + "/payment/success?session_id=" + disponible.get().getStripeSessionId());
            return response;
        }
        if (plan.getPrecio().signum() > 0) requireTestStripe();
        var pendiente = compras.findByUsuario_IdUsuarioAndEmpresaIsNullAndEstadoOrderByIdCompraPlanAsc(usuario.getIdUsuario(), "pendiente")
                .stream().filter(c -> c.getPlan().getIdPlan().equals(planId) && c.getStripeSessionId() != null)
                .reduce((previous, current) -> current);
        if (pendiente.isPresent()) {
            var pending = pendiente.get();
            JsonNode session = consultarSesionStripe(pending.getStripeSessionId());
            if ("paid".equalsIgnoreCase(session.path("payment_status").asText(""))) {
                activarCompra(pending, session);
                response.setSessionId(pending.getStripeSessionId());
                response.setCheckoutUrl(origin + "/payment/success?session_id=" + pending.getStripeSessionId());
                return response;
            }
            if ("open".equals(session.path("status").asText(""))
                    && session.path("success_url").asText("").startsWith(origin + "/payment/success?")
                    && pending.getCheckoutUrl() != null) {
                response.setSessionId(pending.getStripeSessionId()); response.setCheckoutUrl(pending.getCheckoutUrl());
                return response;
            }
        }
        var compra = new com.pos.pagos.domain.CompraPlan(); compra.setUsuario(usuario); compra.setPlan(plan);
        compras.saveAndFlush(compra);
        if (plan.getPrecio().signum() == 0) {
            compra.setEstado("pagado"); compra.setFechaPago(LocalDate.now());
            compra.setStripeSessionId("free_" + UUID.randomUUID());
            compra.setCheckoutUrl(origin + "/payment/success?session_id=" + compra.getStripeSessionId());
        } else {
            JsonNode session = crearSesionStripe(plan, "compra_" + compra.getIdCompraPlan(),
                    "&metadata[id_compra_plan]=" + compra.getIdCompraPlan(), origin, "plan=" + planId);
            compra.setStripeSessionId(session.path("id").asText("")); compra.setCheckoutUrl(session.path("url").asText(""));
            if (compra.getStripeSessionId().isBlank() || compra.getCheckoutUrl().isBlank()) throw ApiException.badRequest("Stripe no devolvió una sesión de pago.");
        }
        compras.save(compra); response.setSessionId(compra.getStripeSessionId()); response.setCheckoutUrl(compra.getCheckoutUrl());
        return response;
    }

    private void requireTestStripe() {
        if (stripeSecret.isBlank()) throw ApiException.badRequest("Configura STRIPE_SECRET_KEY con una clave sk_test_ para abrir Stripe de prueba.");
        if (testOnly && !stripeSecret.startsWith("sk_test_") && !stripeSecret.startsWith("rk_test_")) throw ApiException.badRequest("La demo solo permite claves de prueba de Stripe.");
    }

    @Transactional
    public PagoDtos.ConfirmarResponse confirmar(Usuario usuario, String sessionId) {
        empresaAccess.requireUsuarioActivo(usuario);
        var compra = compras.findByStripeSessionId(sessionId);
        if (compra.isPresent()) {
            var purchase = compra.get();
            if (!purchase.getUsuario().getIdUsuario().equals(usuario.getIdUsuario())) throw ApiException.notFound("No se encontró el pago para esta cuenta.");
            if ("pendiente".equals(purchase.getEstado())) activarCompra(purchase, consultarSesionStripe(sessionId));
            var result = new PagoDtos.ConfirmarResponse(); result.setCompra(compraService.response(purchase));
            result.setMensaje("pagado".equals(purchase.getEstado()) ? "Plan adquirido. Puedes crear tu empresa cuando quieras." : "El pago todavía no está confirmado.");
            return result;
        }
        HistorialSuscripcion historial = historialRepository.findByStripeSessionId(sessionId).orElse(null);
        if (historial == null) {
            throw ApiException.badRequest("session_id inválido o no encontrado.");
        }
        empresaAccess.requireEmpresaSinPlan(usuario, historial.getEmpresa().getIdEmpresa());
        if (!sessionId.startsWith("free_") && !sessionId.startsWith("stub_") && "pendiente".equalsIgnoreCase(historial.getEstado())) {
            activarSiPagada(historial, consultarSesionStripe(sessionId));
        }
        PagoDtos.ConfirmarResponse response = new PagoDtos.ConfirmarResponse();
        response.setMensaje("activo".equalsIgnoreCase(historial.getEstado())
                ? "Suscripción activa. Ya puedes entrar a la empresa."
                : "El pago todavía no está confirmado.");
        response.setSuscripcion(toSuscripcion(historial));
        return response;
    }

    @Transactional
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
                compras.findByStripeSessionId(sessionId).ifPresent(compra -> activarCompra(compra, session));
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("No se pudo leer el evento de Stripe.");
        }
        return Map.of("received", true);
    }

    private JsonNode crearSesionStripe(Plan plan, String reference, String metadata, String origin, String cancelQuery) {
        int centavos = plan.getPrecio().movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
        String success = url(origin + "/payment/success?session_id={CHECKOUT_SESSION_ID}");
        String cancel = url(origin + "/payment/cancel?" + cancelQuery);
        String nombre = url(plan.getNombre() == null ? "Plan" : plan.getNombre());
        String form = "mode=payment"
                + "&success_url=" + success
                + "&cancel_url=" + cancel
                + "&client_reference_id=" + reference
                + metadata
                + "&metadata[id_plan]=" + plan.getIdPlan()
                + "&line_items[0][quantity]=1"
                + "&payment_method_types[0]=card"
                + "&line_items[0][price_data][currency]=bob"
                + "&line_items[0][price_data][unit_amount]=" + centavos
                + "&line_items[0][price_data][product_data][name]=" + nombre;
        return stripe(form, "/v1/checkout/sessions");
    }

    private void activarCompra(com.pos.pagos.domain.CompraPlan compra, JsonNode session) {
        if (!"paid".equalsIgnoreCase(session.path("payment_status").asText(""))) return;
        entityManager.lock(compra, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); entityManager.refresh(compra);
        if (!"pendiente".equals(compra.getEstado())) return;
        compra.setEstado("pagado"); compra.setFechaPago(LocalDate.now()); compras.save(compra);
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
        historial.setFechaFin(plan.getPrecio().signum() == 0 ? null : LocalDate.now().plusDays(30));
        historial.setEstado(estado);
        historial.setStripeSessionId(sessionId);
        historial.setStripePaymentIntentId(paymentIntent);
        historial.setStripePaymentStatus(paymentStatus);
        if ("activo".equals(estado)) cerrarSuscripcionesAnteriores(empresa.getIdEmpresa(), null);
        historialRepository.save(historial);
    }

    private void activarSiPagada(HistorialSuscripcion historial, JsonNode session) {
        String paymentStatus = session.path("payment_status").asText("");
        if (!"paid".equalsIgnoreCase(paymentStatus)) {
            return;
        }
        entityManager.lock(historial.getEmpresa(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(historial);
        // Un webhook repetido de un plan ya reemplazado no puede deshacer un cambio posterior.
        if (!"pendiente".equalsIgnoreCase(historial.getEstado())) return;
        cerrarSuscripcionesAnteriores(historial.getEmpresa().getIdEmpresa(), historial.getIdHistorialSuscripcion());
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

    private void cerrarSuscripcionesAnteriores(Integer idEmpresa, Integer exceptId) {
        for (HistorialSuscripcion old : historialRepository.findActivasPorEmpresa(idEmpresa, LocalDate.now())) {
            if (!old.getIdHistorialSuscripcion().equals(exceptId)) {
                old.setEstado("reemplazada");
                historialRepository.save(old);
            }
        }
    }

    private void requireAdministrator(Usuario usuario, Integer companyId) {
        Long count = entityManager.createQuery("select count(ur) from UsuarioRol ur where ur.usuario.idUsuario = :user and ur.usuario.activo=true and ur.idEmpresa = :company and ur.activo = true and ur.rol.activo=true and ur.rol.idEmpresa is null and ur.rol.tipo='SISTEMA' and ur.rol.protegido=true and ur.rol.nombre = 'ADMINISTRADOR'", Long.class)
                .setParameter("user", usuario.getIdUsuario()).setParameter("company", companyId).getSingleResult();
        if (count == 0) throw ApiException.forbidden("Solo el administrador puede cambiar el plan de la empresa.");
    }

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

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
        dto.setPlanNombre(historial.getPlan().getNombre());
        dto.setFechaInicio(historial.getFechaInicio());
        dto.setFechaFin(historial.getFechaFin());
        dto.setEstado(historial.getEstado());
        dto.setStripeSessionId(historial.getStripeSessionId());
        dto.setStripePaymentIntentId(historial.getStripePaymentIntentId());
        dto.setStripePaymentStatus(historial.getStripePaymentStatus());
        return dto;
    }
}
