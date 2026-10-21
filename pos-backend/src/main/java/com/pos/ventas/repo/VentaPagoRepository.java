package com.pos.ventas.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.ventas.domain.VentaPago;
import com.pos.ventas.domain.VentaPagoId;

public interface VentaPagoRepository extends JpaRepository<VentaPago, VentaPagoId> {

    List<VentaPago> findByIdVenta(Integer idVenta);
}
