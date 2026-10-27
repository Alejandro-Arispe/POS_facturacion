package com.pos.usuarios.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pos.usuarios.domain.RolPermiso;

public interface RolPermisoRepository extends JpaRepository<RolPermiso, Integer> {
    @Query("select rp from RolPermiso rp join fetch rp.rol join fetch rp.permiso where rp.permiso.idPermiso in :ids order by rp.idRolPermiso")
    List<RolPermiso> findForPermissionMerge(@Param("ids") java.util.Collection<Integer> ids);
    @Query("""
        select rp from RolPermiso rp join fetch rp.permiso p join fetch p.modulo
        where rp.rol.idRol in :ids and rp.activo = true and p.activo = true
        order by p.idPermiso
        """)
    List<RolPermiso> findConcesiones(@Param("ids") java.util.Collection<Integer> ids);

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
