package com.pos.empresas.dto;

import java.math.BigDecimal;
import java.util.List;

public record PlanConModulosResponse(
		Integer idPlan,
		String nombre,
		String descripcion,
		BigDecimal precio,
		List<PlanModuloResponse> planModulos) {
}
