package com.pos.empresas.dto;

import java.math.BigDecimal;

import com.pos.empresas.domain.Plan;

public record PlanResponse(Integer idPlan, String nombre, String descripcion, BigDecimal precio) {

    public static PlanResponse from(Plan plan) {
        return new PlanResponse(plan.getIdPlan(), plan.getNombre(), plan.getDescripcion(), plan.getPrecio());
    }
}
