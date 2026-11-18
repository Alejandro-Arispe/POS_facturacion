package com.pos.empresas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmpresaCreate(
		@NotBlank String nombre,
		@NotBlank String razonSocial,
		@NotBlank @Pattern(regexp = "\\d{7,13}", message = "El NIT debe tener solo dígitos (7 a 13).") String nit,
		@NotBlank @Email String correo,
        Integer idCompraPlan) {
    public EmpresaCreate(String nombre, String razonSocial, String nit, String correo) {
        this(nombre, razonSocial, nit, correo, null);
    }
}
