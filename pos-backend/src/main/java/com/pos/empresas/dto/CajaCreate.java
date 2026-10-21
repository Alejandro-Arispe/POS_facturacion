package com.pos.empresas.dto;

import jakarta.validation.constraints.NotBlank;

public record CajaCreate(
		@NotBlank String nombre,
		@NotBlank String codigo) {
}
