package com.pos.ventas.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.ventas.domain.AutorizacionCredito;

public interface AutorizacionCreditoRepository extends JpaRepository<AutorizacionCredito, Integer> {
}
