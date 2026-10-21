package com.pos.empresas.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pos.empresas.domain.Sucursal;

public interface SucursalRepository extends JpaRepository<Sucursal, Integer> {

	List<Sucursal> findByEmpresa_IdEmpresa(Integer idEmpresa);

	List<Sucursal> findByEmpresa_IdEmpresaAndActivoTrue(Integer idEmpresa);

	Optional<Sucursal> findByIdSucursalAndEmpresa_IdEmpresa(Integer idSucursal, Integer idEmpresa);

	long countByEmpresa_IdEmpresaAndActivoTrue(Integer idEmpresa);

	@Query("""
			select distinct s from Sucursal s
			join fetch s.empresa
			where s.empresa.idEmpresa = :idEmpresa
			  and s.idSucursal in (
			    select ur.idSucursal from UsuarioRol ur
			    where ur.usuario.idUsuario = :idUsuario
			      and ur.idEmpresa = :idEmpresa
			      and ur.activo = true
			      and ur.idSucursal is not null
			  )
			""")
	List<Sucursal> findAsignadasAUsuario(
			@Param("idEmpresa") Integer idEmpresa,
			@Param("idUsuario") Integer idUsuario);
}
