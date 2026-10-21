package com.pos.empresas.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimientoCajaResponse(
		Integer idMovimientoCaja,
		Integer idMetodoPago,
		Integer idTipoMovimientoCaja,
		Integer idCajaSesion,
		Integer idUsuario,
		LocalDateTime fecha,
		BigDecimal monto,
		String concepto) {
}
