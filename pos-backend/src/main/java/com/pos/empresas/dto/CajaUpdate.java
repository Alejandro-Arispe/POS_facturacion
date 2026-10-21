package com.pos.empresas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CajaUpdate(
		@NotBlank String nombre,
		@NotBlank String codigo,
		@NotNull Boolean activo) {
}
