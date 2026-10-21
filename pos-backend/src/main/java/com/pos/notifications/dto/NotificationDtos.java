package com.pos.notifications.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RegisterTokenRequest {
        private String token;
        private String uidUsuario;
        private String rol;
        private String plataforma;
        private Integer idEmpresa;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SendAlertRequest {
        private Integer idEmpresa;
        private String titulo;
        private String mensaje;
        private Map<String, Object> payload;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MarkReadRequest {
        private Integer id;
    }
}
