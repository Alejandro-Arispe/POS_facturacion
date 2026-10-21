package com.pos.usuarios.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.usuarios.domain.Modulo;

public interface ModuloRepository extends JpaRepository<Modulo, Integer> {

	Optional<Modulo> findByCodigoIgnoreCase(String codigo);

	List<Modulo> findAllByOrderByIdModuloAsc();
}
