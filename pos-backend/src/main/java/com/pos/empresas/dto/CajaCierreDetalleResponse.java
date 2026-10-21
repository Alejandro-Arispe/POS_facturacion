package com.pos.empresas.dto;

import java.math.BigDecimal;

public record CajaCierreDetalleResponse(
		Integer idCajaCierreDetalle,
		Integer idMetodoPago,
		Integer idCajaSesion,
		BigDecimal montoEsperado,
		BigDecimal montoReal,
		BigDecimal diferencia,
		String observacion) {
}
