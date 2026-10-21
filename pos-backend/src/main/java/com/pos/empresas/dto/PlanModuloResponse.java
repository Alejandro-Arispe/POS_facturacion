package com.pos.empresas.dto;

public record PlanModuloResponse(
		Integer idPlanModulo,
		Integer idPlan,
		Integer idModulo,
		String configuracion,
		ModuloResponse modulo) {
}
