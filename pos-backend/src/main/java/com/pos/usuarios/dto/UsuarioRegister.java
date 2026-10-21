package com.pos.usuarios.dto;

import java.time.LocalDate;

public record UsuarioRegister(
        String email,
        String contrasena,
        String nombreCompleto,
        LocalDate fechaNacimiento,
        String genero,
        String telefono,
        String documento) {
}
