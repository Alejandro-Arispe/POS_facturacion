package com.pos.empresas.dto;

import java.math.BigDecimal;
import java.util.List;

public record MovimientoCajaPorMetodoPagoResponse(
		Integer idMetodoPago,
		String metodoPago,
		BigDecimal totalIngresos,
		BigDecimal totalEgresos,
		BigDecimal montoEsperado,
		List<MovimientoCajaResponse> movimientos) {
}
