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
            select distinct s from Sucursal s join fetch s.empresa
            where s.empresa.idEmpresa=:idEmpresa and exists (
                select ur.idUsuarioRol from UsuarioRol ur join ur.rol r
                where ur.usuario.idUsuario=:idUsuario and ur.usuario.activo=true and ur.idEmpresa=:idEmpresa
                and ur.activo=true and r.activo=true
                and (r.idEmpresa=:idEmpresa or (r.idEmpresa is null and r.tipo='SISTEMA'))
                and (ur.idSucursal=s.idSucursal or ur.idSucursal is null
                    or (r.idEmpresa is null and r.tipo='SISTEMA' and r.protegido=true and r.nombre='ADMINISTRADOR'))
            )
            """)
	List<Sucursal> findAsignadasAUsuario(
			@Param("idEmpresa") Integer idEmpresa,
			@Param("idUsuario") Integer idUsuario);
}
