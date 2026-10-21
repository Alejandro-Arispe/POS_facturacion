package com.pos.empresas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmpresaCreate(
		@NotBlank String nombre,
		@NotBlank String razonSocial,
		@NotBlank String nit,
		@NotBlank @Email String correo) {
}
