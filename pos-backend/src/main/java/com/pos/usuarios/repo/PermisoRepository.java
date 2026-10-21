package com.pos.usuarios.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.pos.usuarios.domain.Permiso;

public interface PermisoRepository extends JpaRepository<Permiso, Integer> {

	List<Permiso> findByModulo_IdModulo(Integer idModulo);

	List<Permiso> findAllByOrderByIdPermisoAsc();

	boolean existsByCodigo(String codigo);

	@Query("SELECT p FROM Permiso p JOIN FETCH p.modulo ORDER BY p.idPermiso ASC")
	List<Permiso> findAllWithModulo();
}
