package com.pos.websocket;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import tools.jackson.databind.ObjectMapper;

@Component
public class ClienteWebSocketHub {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public ClienteWebSocketHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(String idUsuario, WebSocketSession session) {
        sessions.computeIfAbsent(idUsuario, key -> new CopyOnWriteArrayList<>()).add(session);
    }

    public void unregister(String idUsuario, WebSocketSession session) {
        List<WebSocketSession> list = sessions.get(idUsuario);
        if (list != null) {
            list.remove(session);
            if (list.isEmpty()) {
                sessions.remove(idUsuario);
            }
        }
    }

    public void send(String idUsuario, Map<String, Object> message) {
        List<WebSocketSession> list = sessions.get(idUsuario);
        if (list == null || list.isEmpty()) {
            return;
        }
        String json = objectMapper.writeValueAsString(message);
        TextMessage payload = new TextMessage(json);
        for (WebSocketSession session : list) {
            if (session.isOpen()) {
                synchronized (session) {
                    try {
                        session.sendMessage(payload);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }
}
