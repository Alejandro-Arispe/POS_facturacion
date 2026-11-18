package com.pos.ventas.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.ventas.domain.Devolucion;

public interface DevolucionRepository extends JpaRepository<Devolucion, Integer> {

    List<Devolucion> findByVenta_IdVentaOrderByFechaAsc(Integer idVenta);
}
