package com.pos.empresas.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.empresas.domain.Empresa;

public interface EmpresaRepository extends JpaRepository<Empresa, Integer> {

	Optional<Empresa> findByNit(String nit);

	Optional<Empresa> findByCorreoIgnoreCase(String correo);

	List<Empresa> findByActivoTrue();
}
