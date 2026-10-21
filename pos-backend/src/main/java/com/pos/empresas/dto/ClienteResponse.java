package com.pos.empresas.dto;

import java.math.BigDecimal;

public record ClienteResponse(
		Integer idCliente,
		Integer idUsuario,
		Integer idCategoriaCliente,
		String codigoCliente,
		BigDecimal saldoCredito,
		BigDecimal limiteCredito,
		Boolean activo) {
}
