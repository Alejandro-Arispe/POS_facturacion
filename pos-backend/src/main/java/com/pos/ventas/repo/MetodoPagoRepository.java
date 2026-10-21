package com.pos.ventas.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.ventas.domain.MetodoPago;

public interface MetodoPagoRepository extends JpaRepository<MetodoPago, Integer> {

	Optional<MetodoPago> findByNombreIgnoreCase(String nombre);
}
