package com.pos.inventario.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.inventario.domain.TipoMovimiento;

public interface TipoMovimientoRepository extends JpaRepository<TipoMovimiento, Integer> {

    Optional<TipoMovimiento> findByNombre(String nombre);

    Optional<TipoMovimiento> findByNombreIgnoreCase(String nombre);

    List<TipoMovimiento> findAllByOrderByIdTipoMovimientoAsc();
}
