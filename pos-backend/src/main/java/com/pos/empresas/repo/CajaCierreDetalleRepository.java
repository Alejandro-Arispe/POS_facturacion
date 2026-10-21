package com.pos.empresas.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.empresas.domain.CajaCierreDetalle;

public interface CajaCierreDetalleRepository extends JpaRepository<CajaCierreDetalle, Integer> {

	List<CajaCierreDetalle> findByCajaSesion_IdCajaSesion(Integer idCajaSesion);
}
