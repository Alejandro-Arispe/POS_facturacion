package com.pos.empresas.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

public record MovimientoCajaCreate(
		String concepto,
		@NotNull BigDecimal monto,
		@NotNull Integer idTipoMovimientoCaja,
		Integer idMetodoPago) {
}
