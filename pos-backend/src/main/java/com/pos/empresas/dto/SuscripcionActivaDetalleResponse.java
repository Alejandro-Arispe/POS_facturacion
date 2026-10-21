package com.pos.empresas.dto;

public record SuscripcionActivaDetalleResponse(
		Integer idHistorialSuscripcion,
		Integer idEmpresa,
		Integer idPlan,
		String fechaInicio,
		String fechaFin,
		String estado,
		PlanConModulosResponse plan) {
}
