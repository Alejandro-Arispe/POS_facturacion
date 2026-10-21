package com.pos.ventas.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.ventas.domain.TipoVenta;

public interface TipoVentaRepository extends JpaRepository<TipoVenta, Integer> {

	java.util.Optional<TipoVenta> findByNombreIgnoreCase(String nombre);
}
