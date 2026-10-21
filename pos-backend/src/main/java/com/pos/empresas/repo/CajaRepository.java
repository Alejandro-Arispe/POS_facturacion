package com.pos.empresas.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pos.empresas.domain.Caja;

public interface CajaRepository extends JpaRepository<Caja, Integer> {

	List<Caja> findBySucursal_IdSucursal(Integer idSucursal);

	List<Caja> findBySucursal_IdSucursalAndActivoTrue(Integer idSucursal);

	Optional<Caja> findByIdCajaAndSucursal_IdSucursal(Integer idCaja, Integer idSucursal);

	@Query("""
			select c from Caja c
			join fetch c.sucursal s
			join fetch s.empresa
			where c.idCaja = :idCaja
			""")
	Optional<Caja> findWithSucursalAndEmpresa(@Param("idCaja") Integer idCaja);
}
