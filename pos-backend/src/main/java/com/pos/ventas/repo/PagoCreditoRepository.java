package com.pos.ventas.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.ventas.domain.PagoCredito;

public interface PagoCreditoRepository extends JpaRepository<PagoCredito, Integer> {

    List<PagoCredito> findByCuentaPorCobrar_IdCxc(Integer idCxc);
}
