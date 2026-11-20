package com.pos.billetera.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.billetera.domain.CobroBilletera;

public interface CobroBilleteraRepository extends JpaRepository<CobroBilletera, String> {
}
