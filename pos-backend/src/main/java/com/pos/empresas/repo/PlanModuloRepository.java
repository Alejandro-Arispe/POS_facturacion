package com.pos.empresas.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.empresas.domain.PlanModulo;

public interface PlanModuloRepository extends JpaRepository<PlanModulo, Integer> {

	List<PlanModulo> findByPlan_IdPlan(Integer idPlan);

	List<PlanModulo> findByIdModulo(Integer idModulo);

	java.util.Optional<PlanModulo> findByPlan_IdPlanAndIdModulo(Integer idPlan, Integer idModulo);
}
