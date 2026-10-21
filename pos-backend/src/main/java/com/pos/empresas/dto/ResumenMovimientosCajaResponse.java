package com.pos.empresas.dto;

import java.math.BigDecimal;
import java.util.List;

public record ResumenMovimientosCajaResponse(
		Integer idCajaSesion,
		BigDecimal montoEsperadoTotal,
		List<MovimientoCajaPorMetodoPagoResponse> resumenPorMetodoPago) {
}
