package com.pos.empresas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EmpresaUpdate(
		@NotBlank String nombre,
		@NotBlank String razonSocial,
		@NotBlank String nit,
		@NotBlank @Email String correo,
		@NotNull Boolean activo) {
}
