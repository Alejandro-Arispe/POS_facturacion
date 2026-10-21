package com.pos.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationHours;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-hours}") long expirationHours) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationHours = expirationHours;
    }

    public String createAccessToken(Integer userId, String email, List<String> roles) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)));
        if (roles != null && !roles.isEmpty()) {
            builder.claim("roles", roles);
            if (roles.size() == 1) {
                builder.claim("rol", roles.get(0));
            }
        }
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    public String createInvitationToken(Integer idEmpresa, Integer idUsuario, Integer idRol, List<Integer> idSucursales, long hours) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("tipo", "invitacion_empleado")
                .claim("id_empresa", idEmpresa)
                .claim("id_usuario", idUsuario)
                .claim("id_rol", idRol)
                .claim("id_sucursales", idSucursales)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(hours, ChronoUnit.HOURS)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
