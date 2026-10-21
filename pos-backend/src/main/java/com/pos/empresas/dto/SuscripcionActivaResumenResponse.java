package com.pos.empresas.dto;

import java.time.LocalDate;

public record SuscripcionActivaResumenResponse(
		String estado,
		LocalDate fechaFin,
		String planNombre) {
}
