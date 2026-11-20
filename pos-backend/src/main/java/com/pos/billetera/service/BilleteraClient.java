package com.pos.billetera.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.pos.common.exception.ApiException;

/**
 * Cliente de la API de la Billetera: cobros QR (API key del comercio) y autorizacion con la tarjeta
 * virtual (clave de adquirente). Los cuerpos se arman con {@link Map} para que la estrategia SNAKE_CASE
 * del proyecto no renombre los campos que espera la billetera (expiresInSeconds, unitPrice, ...).
 */
@Component
public class BilleteraClient {

    /** Tolerancia del timestamp de la firma del webhook, para evitar repeticiones. */
    private static final long TOLERANCIA_FIRMA_SEGUNDOS = 300;

    private final RestClient http;
    private final ObjectMapper json;
    private final String apiKey;
    private final String webhookSecret;
    private final String acquirerKey;
    private final boolean configurada;

    public BilleteraClient(ObjectMapper json,
            @Value("${app.billetera.api-url:}") String apiUrl,
            @Value("${app.billetera.api-key:}") String apiKey,
            @Value("${app.billetera.webhook-secret:}") String webhookSecret,
            @Value("${app.billetera.acquirer-key:}") String acquirerKey) {
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.acquirerKey = acquirerKey == null ? "" : acquirerKey.trim();
        String url = apiUrl == null ? "" : apiUrl.trim();
        this.configurada = !url.isBlank();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        this.http = RestClient.builder().baseUrl(url.isBlank() ? "http://localhost" : url).requestFactory(factory).build();
    }

    /** Hay URL y API key: los cobros QR van por la billetera real. */
    public boolean qrHabilitado() {
        return configurada && !apiKey.isBlank();
    }

    /** Hay URL y clave de adquirente: la tarjeta se autoriza contra la billetera real. */
    public boolean tarjetaHabilitada() {
        return configurada && !acquirerKey.isBlank();
    }

    public JsonNode crearCobro(BigDecimal monto, String referencia, String descripcion, List<Map<String, Object>> items) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", monto.setScale(2, RoundingMode.HALF_UP).toPlainString());
        body.put("currency", "BOB");
        body.put("reference", referencia);
        body.put("expiresInSeconds", 300);
        if (descripcion != null && !descripcion.isBlank()) body.put("description", descripcion);
        if (items != null && !items.isEmpty()) body.put("items", items);
        return llamar(HttpMethod.POST, "/payments/intents", apiKey, body);
    }

    public JsonNode obtenerCobro(String id) {
        return llamar(HttpMethod.GET, "/payments/intents/" + id, apiKey, null);
    }

    public JsonNode cancelarCobro(String id) {
        return llamar(HttpMethod.POST, "/payments/intents/" + id + "/cancel", apiKey, Map.of());
    }

    public JsonNode autorizarTarjeta(Map<String, Object> body) {
        return llamar(HttpMethod.POST, "/cards/authorizations", acquirerKey, body);
    }

    private JsonNode llamar(HttpMethod metodo, String ruta, String key, Object body) {
        if (!configurada || key.isBlank())
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "La billetera no está configurada en este servidor.");
        try {
            var request = http.method(metodo).uri(ruta).header("x-api-key", key).accept(MediaType.APPLICATION_JSON);
            if (body != null) request = request.contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(body));
            String respuesta = request.retrieve().body(String.class);
            return json.readTree(respuesta == null ? "{}" : respuesta);
        } catch (RestClientResponseException e) {
            String mensaje = e.getResponseBodyAsString();
            try {
                mensaje = json.readTree(mensaje).path("message").asText(mensaje);
            } catch (Exception ignored) {
                // El cuerpo no era JSON: se informa tal cual.
            }
            throw ApiException.badRequest("Billetera: " + mensaje);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo conectar con la billetera.");
        }
    }

    /**
     * Verifica la cabecera {@code Billetera-Signature: t=...,v1=...} sobre el cuerpo crudo:
     * v1 = HMAC-SHA256(secreto, "t.cuerpo"). Rechaza firmas invalidas o de mas de 5 minutos.
     */
    public JsonNode verificarWebhook(String cuerpo, String firma) {
        if (firma == null || webhookSecret.isBlank()) throw ApiException.badRequest("Webhook sin firma.");
        String t = null;
        String v1 = null;
        for (String parte : firma.split(",")) {
            String[] kv = parte.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("t")) t = kv[1];
            if (kv[0].equals("v1")) v1 = kv[1];
        }
        if (t == null || v1 == null) throw ApiException.badRequest("Firma de webhook inválida.");
        try {
            if (Math.abs(Instant.now().getEpochSecond() - Long.parseLong(t)) > TOLERANCIA_FIRMA_SEGUNDOS)
                throw ApiException.badRequest("Webhook vencido.");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] esperado = mac.doFinal((t + "." + cuerpo).getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(esperado, HexFormat.of().parseHex(v1)))
                throw ApiException.badRequest("Firma de webhook inválida.");
            return json.readTree(cuerpo);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("Firma de webhook inválida.");
        }
    }
}
