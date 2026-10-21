package com.pos.empresas.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

public record CajaCierreDetalleCreate(
		@NotNull Integer idMetodoPago,
		@NotNull BigDecimal montoEsperado,
		@NotNull BigDecimal montoReal,
		@NotNull BigDecimal diferencia,
		String observacion) {
}
