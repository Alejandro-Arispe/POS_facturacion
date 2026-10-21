package com.pos.empresas.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.empresas.domain.TipoMovimientoCaja;

public interface TipoMovimientoCajaRepository extends JpaRepository<TipoMovimientoCaja, Integer> {

	Optional<TipoMovimientoCaja> findByNombreIgnoreCase(String nombre);

	List<TipoMovimientoCaja> findAllByOrderByIdTipoMovimientoCajaAsc();
}
