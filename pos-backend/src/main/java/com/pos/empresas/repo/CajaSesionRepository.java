package com.pos.empresas.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pos.empresas.domain.CajaSesion;

public interface CajaSesionRepository extends JpaRepository<CajaSesion, Integer> {

	List<CajaSesion> findByCaja_IdCaja(Integer idCaja);

	List<CajaSesion> findByCaja_IdCajaAndEstado(Integer idCaja, String estado);

	List<CajaSesion> findByEstado(String estado);

	List<CajaSesion> findByIdUsuario(Integer idUsuario);

	Optional<CajaSesion> findFirstByCaja_IdCajaAndEstado(Integer idCaja, String estado);

	Optional<CajaSesion> findFirstByIdUsuarioAndEstado(Integer idUsuario, String estado);

	@Query("""
			select cs from CajaSesion cs
			join fetch cs.caja
			where cs.idUsuario = :idUsuario
			  and cs.estado = :estado
			""")
	List<CajaSesion> findAbiertasPorUsuario(
			@Param("idUsuario") Integer idUsuario,
			@Param("estado") String estado);

	@Query("""
			select cs from CajaSesion cs
			join fetch cs.caja c
			join fetch c.sucursal s
			join fetch s.empresa
			where cs.idCajaSesion = :id
			""")
	Optional<CajaSesion> findWithCajaSucursalEmpresa(@Param("id") Integer id);
}
