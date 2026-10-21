package com.pos.websocket;

import java.net.URI;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ClienteWebSocketHandler extends TextWebSocketHandler {

    private final ClienteWebSocketHub hub;

    public ClienteWebSocketHandler(ClienteWebSocketHub hub) {
        this.hub = hub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String idUsuario = lastSegment(session.getUri());
        session.getAttributes().put("idUsuario", idUsuario);
        hub.register(idUsuario, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // keep-alive
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String idUsuario = (String) session.getAttributes().get("idUsuario");
        if (idUsuario != null) {
            hub.unregister(idUsuario, session);
        }
    }

    private String lastSegment(URI uri) {
        if (uri == null) {
            return "0";
        }
        String path = uri.getPath();
        int idx = path.lastIndexOf('/');
        return path.substring(idx + 1);
    }
}
