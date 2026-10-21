package com.pos.empresas.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CajaSesionResponse(
		Integer idCajaSesion,
		Integer idCaja,
		Integer idUsuario,
		LocalDateTime fechaApertura,
		LocalDateTime fechaCierre,
		BigDecimal montoInicial,
		BigDecimal montoFinal,
		String estado,
		String nota) {
}
