package com.pos.empresas.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pos.empresas.domain.MovimientoCaja;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Integer> {

	List<MovimientoCaja> findByCajaSesion_IdCajaSesion(Integer idCajaSesion);

	List<MovimientoCaja> findByIdUsuario(Integer idUsuario);

	@Query("""
			select m from MovimientoCaja m
			join fetch m.tipoMovimientoCaja
			where m.cajaSesion.idCajaSesion = :idCajaSesion
			order by m.fecha asc, m.idMovimientoCaja asc
			""")
	List<MovimientoCaja> findBySesionFetchTipo(@Param("idCajaSesion") Integer idCajaSesion);
}
