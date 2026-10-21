package com.pos.empresas.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.empresas.dto.PlanResponse;
import com.pos.empresas.repo.PlanRepository;

@Service
public class PlanService {

    private final PlanRepository planRepository;

    public PlanService(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listar() {
        return planRepository.findAllByOrderByIdPlanAsc().stream()
                .map(PlanResponse::from)
                .toList();
    }
}
