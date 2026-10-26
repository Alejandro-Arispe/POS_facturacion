package com.pos.empresas.dto;

import java.math.BigDecimal;

import com.pos.empresas.domain.Plan;

public record PlanResponse(Integer idPlan, String nombre, String descripcion, BigDecimal precio,
        String moneda, com.pos.empresas.service.PlanPolicy.Benefits limites) {

    public static PlanResponse from(Plan plan) {
        return from(plan, com.pos.empresas.service.PlanPolicy.defaults(plan));
    }

    public static PlanResponse from(Plan plan, com.pos.empresas.service.PlanPolicy.Benefits limites) {
        return new PlanResponse(plan.getIdPlan(), plan.getNombre(), plan.getDescripcion(), plan.getPrecio(), "BOB", limites);
    }
}
