package com.pos.pagos.service;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.pos.common.exception.ApiException;

@Component
public class CheckoutReturnUrls {
    private final String fallback;
    private final Set<String> allowed = new HashSet<>();
    public CheckoutReturnUrls(@Value("${app.frontend-base-url}") String fallback,
            @Value("${app.cors.origins}") String origins) {
        this.fallback = fallback.replaceAll("/+$", "");
        for (String origin : origins.split(",")) allowed.add(origin.trim().replaceAll("/+$", ""));
        allowed.add(this.fallback);
    }
    public String origin(String requested) {
        if (requested == null || requested.isBlank()) return fallback;
        try {
            URI uri = URI.create(requested);
            if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/"))) throw new IllegalArgumentException();
            String normalized = requested.replaceAll("/+$", "");
            if (!allowed.contains(normalized)) throw new IllegalArgumentException();
            return normalized;
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("El origen de retorno del pago no está permitido.");
        }
    }
}
