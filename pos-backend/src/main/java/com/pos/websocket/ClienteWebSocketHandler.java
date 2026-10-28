package com.pos.websocket;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;
@Component
public class ClienteWebSocketHandler extends TextWebSocketHandler {
    private final ClienteWebSocketHub hub; private final SocketIdentity identity; private final ObjectMapper json;
    public ClienteWebSocketHandler(ClienteWebSocketHub hub,SocketIdentity identity,ObjectMapper json){this.hub=hub;this.identity=identity;this.json=json;}
    @Override public void afterConnectionEstablished(WebSocketSession session){session.setTextMessageSizeLimit(8192);}
    @Override protected void handleTextMessage(WebSocketSession session,TextMessage message) throws Exception {
        try {
            if(session.getAttributes().containsKey("accessToken")){identity.current(session);return;}
            var node=json.readTree(message.getPayload());
            if(!"autenticar".equals(node.path("tipo").asText())) throw new IllegalArgumentException();
            String token=node.path("token").asText();var user=identity.verify(token);
            String id=session.getUri().getPath().substring(session.getUri().getPath().lastIndexOf('/')+1);
            if(!id.equals(String.valueOf(user.getIdUsuario()))) throw new IllegalArgumentException();
            session.getAttributes().put("accessToken",token);session.getAttributes().put("idUsuario",id);hub.register(id,session);
        } catch(Exception ex){session.close(CloseStatus.POLICY_VIOLATION);}
    }
    @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status){String id=(String)session.getAttributes().get("idUsuario");if(id!=null)hub.unregister(id,session);session.getAttributes().remove("accessToken");}
}
