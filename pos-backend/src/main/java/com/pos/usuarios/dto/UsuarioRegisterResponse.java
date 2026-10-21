package com.pos.usuarios.dto;

public record UsuarioRegisterResponse(
        Integer usuarioId,
        String email,
        boolean activo,
        String mensaje,
        boolean emailEnviado) {
}
