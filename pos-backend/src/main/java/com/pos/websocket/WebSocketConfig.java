package com.pos.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AdministradorWebSocketHandler administradorWebSocketHandler;
    private final ClienteWebSocketHandler clienteWebSocketHandler;

    public WebSocketConfig(
            AdministradorWebSocketHandler administradorWebSocketHandler,
            ClienteWebSocketHandler clienteWebSocketHandler) {
        this.administradorWebSocketHandler = administradorWebSocketHandler;
        this.clienteWebSocketHandler = clienteWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(administradorWebSocketHandler, "/ws/administrador/{idEmpresa}")
                .setAllowedOriginPatterns("*");
        registry.addHandler(clienteWebSocketHandler, "/ws/clientes/{idUsuario}")
                .setAllowedOriginPatterns("*");
    }
}
