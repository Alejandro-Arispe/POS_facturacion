package com.pos.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import com.pos.common.exception.ApiException;
import com.pos.common.security.JwtService;
import com.pos.usuarios.repo.UsuarioRepository;
import com.pos.usuarios.domain.Usuario;

@Component
public class SocketIdentity {
    private final JwtService jwt;
    private final UsuarioRepository users;
    public SocketIdentity(JwtService jwt,UsuarioRepository users) {this.jwt=jwt;this.users=users;}
    public Usuario verify(String token) {
        try {
            var claims=jwt.parse(token);
            if(claims.get("tipo")!=null) throw new IllegalArgumentException();
            var user=users.findById(Integer.valueOf(claims.getSubject())).orElse(null);
            if(user==null || !Boolean.TRUE.equals(user.getActivo())) throw new IllegalArgumentException();
            return user;
        } catch(Exception ex) {throw ApiException.unauthorized("Sesión de WebSocket inválida o expirada.");}
    }
    public Usuario current(WebSocketSession session) {return verify((String)session.getAttributes().get("accessToken"));}
}
