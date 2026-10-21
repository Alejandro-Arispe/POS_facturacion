package com.pos.empresas.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CajaSesionCierreResponse(
		Integer idCajaSesion,
		BigDecimal montoInicial,
		BigDecimal montoTotalReal,
		BigDecimal montoFinal,
		String estado,
		LocalDateTime fechaCierre,
		List<MovimientoCajaResponse> movimientoCierre,
		List<CajaCierreDetalleResponse> cierres) {
}
