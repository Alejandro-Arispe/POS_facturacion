package com.pos.siat.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.siat.domain.SinRecepcion;

public interface SinRecepcionRepository extends JpaRepository<SinRecepcion, String> {
}
