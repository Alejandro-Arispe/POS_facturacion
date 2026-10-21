package com.pos.empresas.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

public record CajaSesionCreate(
		@NotNull BigDecimal montoInicial,
		String nota) {
}
