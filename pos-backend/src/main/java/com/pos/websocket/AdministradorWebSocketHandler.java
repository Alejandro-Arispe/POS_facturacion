package com.pos.websocket;

import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

@Component
public class AdministradorWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper json;
    private final AdminDashboardService dashboard;
    private final SocketIdentity identity;
    private final ScheduledExecutorService scheduler=Executors.newScheduledThreadPool(2);
    private final Map<String,ScheduledFuture<?>> tasks=new ConcurrentHashMap<>();
    public AdministradorWebSocketHandler(ObjectMapper json,AdminDashboardService dashboard,SocketIdentity identity) {
        this.json=json;this.dashboard=dashboard;this.identity=identity;
    }
    @Override public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(8192);
        tasks.put(session.getId(),scheduler.schedule(() -> close(session),10,TimeUnit.SECONDS));
    }
    @Override protected void handleTextMessage(WebSocketSession session,TextMessage message) throws Exception {
        try {
            var node=json.readTree(message.getPayload());
            if(!session.getAttributes().containsKey("accessToken")) {
                if(!"autenticar".equals(node.path("tipo").asText())) {close(session);return;}
                String token=node.path("token").asText(); identity.verify(token);
                Integer company=Integer.valueOf(session.getUri().getPath().substring(session.getUri().getPath().lastIndexOf('/')+1));
                session.getAttributes().put("accessToken",token);session.getAttributes().put("idEmpresa",company);
                if(node.hasNonNull("id_sucursal")) session.getAttributes().put("idSucursal",node.get("id_sucursal").asInt());
                send(session);
                var old=tasks.remove(session.getId());if(old!=null) old.cancel(false);
                tasks.put(session.getId(),scheduler.scheduleAtFixedRate(() -> {try {send(session);} catch(Exception ex){close(session);}},5,5,TimeUnit.SECONDS));
            } else if("dashboard".equals(node.path("tipo").asText())) {
                Integer branch=null;
                if(node.hasNonNull("id_sucursal")) branch=node.get("id_sucursal").asInt();
                else if(node.path("datos").isArray() && node.path("datos").size()>0 && node.path("datos").get(0).hasNonNull("id_sucursal")) branch=node.path("datos").get(0).get("id_sucursal").asInt();
                if(branch==null || branch==0) session.getAttributes().remove("idSucursal");else session.getAttributes().put("idSucursal",branch);
                send(session);
            }
        } catch(Exception ex) {close(session);}
    }
    private void send(WebSocketSession session) throws Exception {
        if(!session.isOpen()) return;
        var user=identity.current(session);
        var data=dashboard.calcular(user,(Integer)session.getAttributes().get("idEmpresa"),(Integer)session.getAttributes().get("idSucursal"));
        synchronized(session) {if(session.isOpen()) session.sendMessage(new TextMessage(json.writeValueAsString(Map.of("tipo","dashboard","datos",List.of(data)))));}
    }
    private void close(WebSocketSession session) {
        var future=tasks.remove(session.getId());if(future!=null) future.cancel(false);
        try {session.close(CloseStatus.POLICY_VIOLATION);}catch(Exception ignored) {}
    }
    @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status) {var future=tasks.remove(session.getId());if(future!=null)future.cancel(false);session.getAttributes().remove("accessToken");}
    @jakarta.annotation.PreDestroy public void stop() {scheduler.shutdownNow();}
}
