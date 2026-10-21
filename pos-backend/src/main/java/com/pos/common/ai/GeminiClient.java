package com.pos.common.ai;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Cliente de Google Gemini (API de Google AI Studio). La clave se configura con GEMINI_API_KEY
 * (variable de entorno o archivo .env del backend). Sin clave, isEnabled() devuelve false y
 * quien lo use debe tener una alternativa local.
 */
@Component
public class GeminiClient {

	private static final int MAX_INTENTOS = 3;
	private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

	private final String apiKey;
	private final String model;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public GeminiClient(
			@Value("${app.ai.gemini.api-key:}") String apiKey,
			@Value("${app.ai.gemini.model:gemini-3.5-flash}") String model,
			ObjectMapper objectMapper) {
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.model = model;
		this.objectMapper = objectMapper;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(5_000);
		factory.setReadTimeout(30_000);
		this.restClient = RestClient.builder().baseUrl(BASE_URL).requestFactory(factory).build();
	}

	public boolean isEnabled() {
		return !apiKey.isBlank();
	}

	public String getModel() {
		return model;
	}

	/**
	 * Pide a Gemini una respuesta en JSON. Devuelve vacio si no hay clave o si la llamada falla,
	 * para que el llamador use su alternativa sin romper la operacion.
	 */
	public Optional<JsonNode> generarJson(String instrucciones, String mensaje) {
		if (!isEnabled()) {
			return Optional.empty();
		}
		Map<String, Object> cuerpo = Map.of(
				"systemInstruction", Map.of("parts", List.of(Map.of("text", instrucciones))),
				"contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", mensaje)))),
				"generationConfig", Map.of(
						"temperature", 0.1,
						"responseMimeType", "application/json"));
		// Google responde 503 cuando el modelo esta saturado: se reintenta unas veces antes de rendirse.
		for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
			try {
				JsonNode respuesta = restClient.post()
						.uri("/models/{model}:generateContent", model)
						.header("x-goog-api-key", apiKey)
						.contentType(MediaType.APPLICATION_JSON)
						.body(cuerpo)
						.retrieve()
						.body(JsonNode.class);
				if (respuesta == null) {
					return Optional.empty();
				}
				String texto = respuesta.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
				if (texto.isBlank()) {
					return Optional.empty();
				}
				return Optional.of(objectMapper.readTree(texto));
			} catch (HttpServerErrorException ex) {
				System.out.println("[GEMINI] Intento " + intento + "/" + MAX_INTENTOS + " fallido: " + ex.getStatusCode());
				if (intento < MAX_INTENTOS) {
					try {
						Thread.sleep(1_500L * intento);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						return Optional.empty();
					}
				}
			} catch (Exception ex) {
				System.out.println("[GEMINI] No se pudo obtener respuesta: " + ex.getMessage());
				return Optional.empty();
			}
		}
		return Optional.empty();
	}
}
