package com.pos.websocket;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AdministradorWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final AdminDashboardService dashboardService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ConcurrentHashMap<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    public AdministradorWebSocketHandler(ObjectMapper objectMapper, AdminDashboardService dashboardService) {
        this.objectMapper = objectMapper;
        this.dashboardService = dashboardService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Integer idEmpresa = lastInt(session.getUri());
        session.getAttributes().put("idEmpresa", idEmpresa);
        sendDashboard(session, idEmpresa, null);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            if (!session.isOpen()) {
                return;
            }
            Integer sucursal = (Integer) session.getAttributes().get("idSucursal");
            try {
                sendDashboard(session, idEmpresa, sucursal);
            } catch (Exception ignored) {
            }
        }, 5, 5, TimeUnit.SECONDS);
        tasks.put(session.getId(), future);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getPayload());
        Integer idEmpresa = (Integer) session.getAttributes().get("idEmpresa");
        if (node.has("tipo") && "dashboard".equals(node.get("tipo").asText())) {
            Integer idSucursal = null;
            JsonNode datos = node.get("datos");
            if (datos != null && datos.isArray() && datos.size() > 0 && datos.get(0).has("id_sucursal")) {
                idSucursal = datos.get(0).get("id_sucursal").asInt();
            } else if (node.has("id_sucursal")) {
                idSucursal = node.get("id_sucursal").asInt();
            }
            if (idSucursal != null && idSucursal == 0) {
                idSucursal = null;
            }
            session.getAttributes().put("idSucursal", idSucursal);
            sendDashboard(session, idEmpresa, idSucursal);
        } else {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                    Map.of("tipo", "error", "datos", List.of(Map.of("mensaje", "Tipo de mensaje no soportado."))))));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ScheduledFuture<?> future = tasks.remove(session.getId());
        if (future != null) {
            future.cancel(true);
        }
    }

    private void sendDashboard(WebSocketSession session, Integer idEmpresa, Integer idSucursal) throws IOException {
        Map<String, Object> dashboard = dashboardService.calcular(idEmpresa, idSucursal);
        Map<String, Object> payload = Map.of("tipo", "dashboard", "datos", List.of(dashboard));
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        }
    }

    private Integer lastInt(URI uri) {
        if (uri == null) {
            return 0;
        }
        String path = uri.getPath();
        int idx = path.lastIndexOf('/');
        return Integer.valueOf(path.substring(idx + 1));
    }
}
