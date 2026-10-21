package com.pos.usuarios.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pos.usuarios.domain.UsuarioRol;

public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, Integer> {

	List<UsuarioRol> findByUsuario_IdUsuarioAndActivoTrue(Integer idUsuario);

	List<UsuarioRol> findByUsuario_IdUsuarioAndIdEmpresaAndActivoTrue(Integer idUsuario, Integer idEmpresa);

	List<UsuarioRol> findByUsuario_IdUsuarioAndIdEmpresa(Integer idUsuario, Integer idEmpresa);

	boolean existsByUsuario_IdUsuarioAndIdEmpresaAndActivoTrue(Integer idUsuario, Integer idEmpresa);

	List<UsuarioRol> findByIdEmpresaAndRol_IdRolAndActivoTrue(Integer idEmpresa, Integer idRol);

	Optional<UsuarioRol> findFirstByUsuario_IdUsuarioAndIdEmpresaAndActivoTrue(Integer idUsuario, Integer idEmpresa);

	Optional<UsuarioRol> findFirstByUsuario_IdUsuarioAndIdEmpresaAndActivoTrueAndIdSucursalIsNull(
			Integer idUsuario, Integer idEmpresa);

	Optional<UsuarioRol> findFirstByUsuario_IdUsuarioAndIdEmpresaAndRol_IdRolAndActivoTrueAndIdSucursalIsNull(
			Integer idUsuario, Integer idEmpresa, Integer idRol);

	Optional<UsuarioRol> findFirstByUsuario_IdUsuarioAndIdEmpresaAndIdSucursalAndRol_IdRolAndActivoTrue(
			Integer idUsuario, Integer idEmpresa, Integer idSucursal, Integer idRol);

	@Query("""
			select ur from UsuarioRol ur
			join fetch ur.rol r
			where ur.usuario.idUsuario = :idUsuario
			  and ur.idEmpresa = :idEmpresa
			  and ur.activo = true
			  and r.nombre <> 'CLIENTE'
			order by ur.idUsuarioRol
			""")
	List<UsuarioRol> findActivosDistintoCliente(
			@Param("idUsuario") Integer idUsuario,
			@Param("idEmpresa") Integer idEmpresa);

	@Query("""
			select ur from UsuarioRol ur
			join fetch ur.usuario u
			left join fetch u.persona
			join fetch ur.rol r
			where ur.idEmpresa = :idEmpresa
			  and r.nombre not in :rolesExcluidos
			order by u.idUsuario, ur.idSucursal, ur.idUsuarioRol
			""")
	List<UsuarioRol> findPersonalExcluyendoRoles(
			@Param("idEmpresa") Integer idEmpresa,
			@Param("rolesExcluidos") Collection<String> rolesExcluidos);

	@Query("""
			select count(distinct ur.usuario.idUsuario)
			from UsuarioRol ur
			join ur.rol r
			where ur.idEmpresa = :idEmpresa
			  and ur.activo = true
			  and r.nombre not in :rolesExcluidos
			""")
	long countUsuariosActivosExcluyendoRoles(
			@Param("idEmpresa") Integer idEmpresa,
			@Param("rolesExcluidos") Collection<String> rolesExcluidos);

	@Query("""
			select ur from UsuarioRol ur
			join fetch ur.usuario u
			left join fetch u.persona
			join fetch ur.rol
			where ur.idEmpresa = :idEmpresa
			  and ur.rol.idRol = :idRol
			  and ur.idSucursal is null
			  and ur.activo = true
			""")
	List<UsuarioRol> findActivosPorEmpresaYRolSinSucursal(
			@Param("idEmpresa") Integer idEmpresa,
			@Param("idRol") Integer idRol);

	@Query("""
			select ur from UsuarioRol ur
			join fetch ur.rol
			where ur.usuario.idUsuario = :idUsuario
			  and ur.rol.idRol = :idRol
			  and ur.idSucursal is not null
			  and ur.activo = true
			  and (:idEmpresa is null or ur.idEmpresa = :idEmpresa)
			""")
	List<UsuarioRol> findSucursalesEmpleado(
			@Param("idUsuario") Integer idUsuario,
			@Param("idRol") Integer idRol,
			@Param("idEmpresa") Integer idEmpresa);
}
