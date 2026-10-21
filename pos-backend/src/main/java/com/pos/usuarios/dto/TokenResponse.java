package com.pos.usuarios.dto;

public record TokenResponse(String accessToken, String tokenType) {

    public TokenResponse(String accessToken) {
        this(accessToken, "bearer");
    }
}
