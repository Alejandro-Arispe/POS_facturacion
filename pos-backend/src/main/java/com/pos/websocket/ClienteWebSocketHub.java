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
    private final SocketIdentity identity;
    private final com.pos.notifications.service.NotificationReadPolicy readPolicy;

    public ClienteWebSocketHub(ObjectMapper objectMapper,SocketIdentity identity,com.pos.notifications.service.NotificationReadPolicy readPolicy) {
        this.objectMapper = objectMapper; this.identity=identity;this.readPolicy=readPolicy;
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
        @SuppressWarnings("unchecked") Map<String,Object> data=message.get("payload") instanceof Map<?,?> p?(Map<String,Object>)p:Map.of();
        Integer company;
        try {company=Integer.valueOf(String.valueOf(data.get("id_empresa")));} catch(Exception ex){return;}
        Map<String,Object> safe=Map.of("tipo","alerta","titulo","Actualización de tu cuenta","mensaje","Consulta tus operaciones autorizadas.","payload",readPolicy.payload(data));
        TextMessage payload = new TextMessage(objectMapper.writeValueAsString(safe));
        for (WebSocketSession session : list) {
            try {if(!readPolicy.visible(identity.current(session),company,data)) continue;}
            catch(Exception ex){try {session.close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);}catch(Exception ignored){}continue;}
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
