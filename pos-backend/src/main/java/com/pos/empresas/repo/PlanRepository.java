package com.pos.empresas.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.empresas.domain.Plan;

public interface PlanRepository extends JpaRepository<Plan, Integer> {

	Optional<Plan> findByNombreIgnoreCase(String nombre);

	List<Plan> findAllByOrderByIdPlanAsc();
}
