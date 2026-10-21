package com.pos.notifications.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.common.exception.ApiException;
import com.pos.notifications.domain.DispositivoToken;
import com.pos.notifications.domain.NotificacionHistorial;
import com.pos.notifications.dto.NotificationDtos;
import com.pos.notifications.repo.DispositivoTokenRepository;
import com.pos.notifications.repo.NotificacionHistorialRepository;
import com.pos.websocket.ClienteWebSocketHub;

@Service
public class NotificationService {

    private final DispositivoTokenRepository tokenRepository;
    private final NotificacionHistorialRepository historialRepository;
    private final ClienteWebSocketHub clienteWebSocketHub;

    public NotificationService(
            DispositivoTokenRepository tokenRepository,
            NotificacionHistorialRepository historialRepository,
            ClienteWebSocketHub clienteWebSocketHub) {
        this.tokenRepository = tokenRepository;
        this.historialRepository = historialRepository;
        this.clienteWebSocketHub = clienteWebSocketHub;
    }

    @Transactional
    public Map<String, Object> registerToken(NotificationDtos.RegisterTokenRequest request) {
        if (request.getToken() == null) {
            throw ApiException.badRequest("Token FCM inválido o simulado");
        }
        String token = request.getToken().trim();
        String lower = token.toLowerCase(Locale.ROOT);
        if (token.isBlank() || lower.equals("null") || lower.equals("none") || lower.equals("mock")
                || lower.startsWith("mock_token")) {
            throw ApiException.badRequest("Token FCM inválido o simulado");
        }
        DispositivoToken existing = tokenRepository.findByToken(token).orElse(null);
        if (existing == null) {
            existing = new DispositivoToken();
            existing.setToken(token);
            existing.setFechaRegistro(LocalDateTime.now());
        }
        existing.setUidUsuario(request.getUidUsuario());
        existing.setRol(request.getRol());
        existing.setPlataforma(request.getPlataforma());
        existing.setIdEmpresa(request.getIdEmpresa());
        tokenRepository.save(existing);
        return Map.of("ok", true, "token_id", existing.getId());
    }

    @Transactional
    public Map<String, Object> enviarAlerta(Integer idEmpresa, String titulo, String mensaje, Map<String, Object> payload) {
        NotificacionHistorial historial = new NotificacionHistorial();
        historial.setIdEmpresa(idEmpresa);
        historial.setPrioridad(0);
        historial.setTipo("ALERTA");
        historial.setTitulo(titulo);
        historial.setMensaje(mensaje);
        historial.setPayload(payload);
        historial.setLeido(Boolean.FALSE);
        historial.setFecha(LocalDateTime.now());
        historialRepository.save(historial);

        List<DispositivoToken> tokens = tokenRepository.findByIdEmpresa(idEmpresa);
        if (payload != null && payload.get("id_usuario") != null) {
            clienteWebSocketHub.send(payload.get("id_usuario").toString(), Map.of(
                    "tipo", "alerta",
                    "titulo", titulo,
                    "mensaje", mensaje,
                    "payload", payload));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sent", 0);
        result.put("failed", 0);
        result.put("reason", "fcm_stub");
        result.put("historial_id", historial.getId());
        result.put("tokens", tokens.size());
        return result;
    }

    @Transactional
    public Map<String, Object> enviarNotificacionUsuario(
            Integer idUsuario, Integer idEmpresa, String titulo, String mensaje, Map<String, Object> payload) {
        List<DispositivoToken> tokens = tokenRepository.findByUidUsuario(String.valueOf(idUsuario));
        NotificacionHistorial historial = new NotificacionHistorial();
        historial.setIdEmpresa(idEmpresa);
        historial.setPrioridad(0);
        historial.setTipo("ALERTA");
        historial.setTitulo(titulo);
        historial.setMensaje(mensaje);
        historial.setPayload(payload);
        historial.setLeido(Boolean.FALSE);
        historial.setFecha(LocalDateTime.now());
        historialRepository.save(historial);
        clienteWebSocketHub.send(String.valueOf(idUsuario), Map.of(
                "tipo", "alerta",
                "titulo", titulo,
                "mensaje", mensaje,
                "payload", payload == null ? Map.of() : payload));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sent", 0);
        result.put("failed", 0);
        result.put("reason", "fcm_stub");
        result.put("historial_id", historial.getId());
        result.put("tokens", tokens.size());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> history(Integer idEmpresa, Integer idUsuario) {
        List<NotificacionHistorial> rows = historialRepository.findByIdEmpresaOrderByFechaDesc(idEmpresa);
        List<Map<String, Object>> items = new ArrayList<>();
        for (NotificacionHistorial row : rows) {
            Map<String, Object> payload = row.getPayload() == null ? Map.of() : row.getPayload();
            if (idUsuario != null) {
                Object uid = payload.get("id_usuario");
                if (uid == null || !String.valueOf(idUsuario).equals(String.valueOf(uid))) {
                    continue;
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("id_empresa", row.getIdEmpresa());
            item.put("prioridad", row.getPrioridad());
            item.put("tipo", row.getTipo());
            item.put("titulo", row.getTitulo());
            item.put("mensaje", row.getMensaje());
            item.put("payload", payload);
            item.put("leido", row.getLeido());
            item.put("fecha", row.getFecha());
            items.add(item);
        }
        return Map.of("items", items);
    }

    @Transactional
    public Map<String, Object> markRead(Integer id) {
        NotificacionHistorial row = historialRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Notificacion no encontrada"));
        row.setLeido(Boolean.TRUE);
        historialRepository.save(row);
        return Map.of("ok", true);
    }
}
