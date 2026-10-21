package com.pos.usuarios.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pos.usuarios.domain.Rol;

public interface RolRepository extends JpaRepository<Rol, Integer> {

	List<Rol> findByIdEmpresa(Integer idEmpresa);

	Optional<Rol> findByNombreIgnoreCase(String nombre);

	List<Rol> findByIdEmpresaAndActivoTrue(Integer idEmpresa);

	Optional<Rol> findByNombreIgnoreCaseAndIdEmpresa(String nombre, Integer idEmpresa);

	@Query("""
			SELECT r FROM Rol r
			WHERE r.activo = true
			  AND (r.idEmpresa IS NULL OR r.tipo = 'SISTEMA' OR r.idEmpresa = :idEmpresa)
			ORDER BY r.idRol ASC
			""")
	List<Rol> findActivosVisiblesPorEmpresa(@Param("idEmpresa") Integer idEmpresa);
}
