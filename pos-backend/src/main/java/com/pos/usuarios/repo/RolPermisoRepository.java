package com.pos.usuarios.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pos.usuarios.domain.RolPermiso;

public interface RolPermisoRepository extends JpaRepository<RolPermiso, Integer> {

	List<RolPermiso> findByRol_IdRol(Integer idRol);

	List<RolPermiso> findByRol_IdRolAndActivoTrue(Integer idRol);

	@Query("""
			select rp from RolPermiso rp
			join fetch rp.permiso p
			join fetch p.modulo
			where rp.rol.idRol = :idRol
			order by p.idPermiso
			""")
	List<RolPermiso> findByRolIdWithPermisoAndModulo(@Param("idRol") Integer idRol);
}
