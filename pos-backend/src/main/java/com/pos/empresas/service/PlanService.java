package com.pos.empresas.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.empresas.dto.PlanResponse;
import com.pos.empresas.repo.PlanRepository;

@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final PlanPolicy policy;

    public PlanService(PlanRepository planRepository, PlanPolicy policy) {
        this.planRepository = planRepository;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listar() {
        return planRepository.findAllByOrderByIdPlanAsc().stream()
                .filter(p -> !"Premium".equalsIgnoreCase(p.getNombre()))
                .sorted(java.util.Comparator.comparing(com.pos.empresas.domain.Plan::getPrecio))
                .map(p -> PlanResponse.from(p, policy.benefits(p)))
                .toList();
    }
}
