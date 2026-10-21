package com.pos.pagos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final String frontendBaseUrl;

    public PagoService(
            EmpresaAccess empresaAccess,
            PlanRepository planRepository,
            HistorialSuscripcionRepository historialRepository,
            @Value("${app.stripe.secret-key:}") String stripeSecret,
            @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.empresaAccess = empresaAccess;
        this.planRepository = planRepository;
        this.historialRepository = historialRepository;
        this.stripeSecret = stripeSecret;
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
        if (stripeSecret == null || stripeSecret.isBlank()) {
            String sessionId = "stub_" + UUID.randomUUID();
            HistorialSuscripcion historial = new HistorialSuscripcion();
            historial.setPlan(plan);
            historial.setEmpresa(empresa);
            historial.setFechaInicio(LocalDate.now());
            historial.setFechaFin(LocalDate.now().plusDays(30));
            historial.setEstado("activo");
            historial.setStripeSessionId(sessionId);
            historial.setStripePaymentStatus("paid");
            historialRepository.save(historial);
            response.setSessionId(sessionId);
            response.setCheckoutUrl(frontendBaseUrl + "/administrator/payment/success?session_id=" + sessionId);
            return response;
        }
        String sessionId = "stripe_" + UUID.randomUUID();
        response.setSessionId(sessionId);
        response.setCheckoutUrl(frontendBaseUrl + "/administrator/payment/success?session_id=" + sessionId);
        return response;
    }

    @Transactional
    public PagoDtos.ConfirmarResponse confirmar(Usuario usuario, String sessionId) {
        empresaAccess.requireUsuarioActivo(usuario);
        HistorialSuscripcion historial = historialRepository.findByStripeSessionId(sessionId).orElse(null);
        if (historial == null) {
            throw ApiException.badRequest("session_id inválido o no encontrado.");
        }
        PagoDtos.ConfirmarResponse response = new PagoDtos.ConfirmarResponse();
        response.setMensaje("Este pago ya fue procesado anteriormente.");
        response.setSuscripcion(toSuscripcion(historial));
        return response;
    }

    public Map<String, Object> webhook() {
        return Map.of("received", true);
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
