package com.pos.empresas.dto;

import jakarta.validation.constraints.NotBlank;

public record SucursalCreate(
		@NotBlank String nombre,
		@NotBlank String direccion,
		@NotBlank String telefono,
		@NotBlank String ciudad) {
}
