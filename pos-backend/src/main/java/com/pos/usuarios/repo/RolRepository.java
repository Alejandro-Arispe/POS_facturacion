package com.pos.usuarios.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pos.usuarios.domain.Rol;

public interface RolRepository extends JpaRepository<Rol, Integer> {

	List<Rol> findByIdEmpresa(Integer idEmpresa);

    /** Protected system namespace; tenant roles with the same historical label cannot impersonate it. */
    @Query("select r from Rol r where upper(r.nombre)=upper(:nombre) and r.idEmpresa is null and r.tipo='SISTEMA'")
	Optional<Rol> findByNombreIgnoreCase(@Param("nombre") String nombre);

    @Query("select r from Rol r where r.idEmpresa=:company or (r.idEmpresa is null and r.tipo='SISTEMA') order by r.idRol")
    List<Rol> findVisiblesPorEmpresa(@Param("company") Integer company);

	List<Rol> findByIdEmpresaAndActivoTrue(Integer idEmpresa);

	Optional<Rol> findByNombreIgnoreCaseAndIdEmpresa(String nombre, Integer idEmpresa);

	@Query("""
			SELECT r FROM Rol r
			WHERE r.activo = true
			  AND ((r.idEmpresa IS NULL AND r.tipo = 'SISTEMA') OR r.idEmpresa = :idEmpresa)
			ORDER BY r.idRol ASC
			""")
	List<Rol> findActivosVisiblesPorEmpresa(@Param("idEmpresa") Integer idEmpresa);
}
