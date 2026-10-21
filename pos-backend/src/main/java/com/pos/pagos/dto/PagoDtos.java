package com.pos.pagos.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class PagoDtos {

    private PagoDtos() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CheckoutRequest {
        private Integer idEmpresa;
        private Integer idPlan;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CheckoutResponse {
        private String checkoutUrl;
        private String sessionId;
        private String planNombre;
        private String monto;
        private String moneda;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConfirmarRequest {
        private String sessionId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SuscripcionResponse {
        private Integer idHistorialSuscripcion;
        private Integer idEmpresa;
        private Integer idPlan;
        private LocalDate fechaInicio;
        private LocalDate fechaFin;
        private String estado;
        private String stripeSessionId;
        private String stripePaymentIntentId;
        private String stripePaymentStatus;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ConfirmarResponse {
        private String mensaje;
        private SuscripcionResponse suscripcion;
    }
}
