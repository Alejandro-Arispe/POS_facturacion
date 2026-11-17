package com.pos.usuarios.dto;

import java.time.LocalDate;

public record UsuarioRegister(
        String email,
        String contrasena,
        String nombreCompleto,
        LocalDate fechaNacimiento,
        String genero,
        String telefono,
        String documento,
        String nombre,
        String apellido) {

    /** Compatibilidad: registro con nombre completo en un solo campo. */
    public UsuarioRegister(String email, String contrasena, String nombreCompleto, LocalDate fechaNacimiento,
            String genero, String telefono, String documento) {
        this(email, contrasena, nombreCompleto, fechaNacimiento, genero, telefono, documento, null, null);
    }
}
