package com.pos.ventas.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.ventas.domain.DetalleVenta;

public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Integer> {

    List<DetalleVenta> findByVenta_IdVenta(Integer idVenta);
}
