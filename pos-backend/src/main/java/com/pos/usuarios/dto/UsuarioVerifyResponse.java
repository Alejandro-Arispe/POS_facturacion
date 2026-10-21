package com.pos.usuarios.dto;

public record UsuarioVerifyResponse(String email, boolean activo, String mensaje) {
}
