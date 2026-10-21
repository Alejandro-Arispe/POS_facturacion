package com.pos.empresas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SucursalUpdate(
		@NotBlank String nombre,
		@NotBlank String direccion,
		@NotBlank String telefono,
		@NotBlank String ciudad,
		@NotNull Boolean activo) {
}
