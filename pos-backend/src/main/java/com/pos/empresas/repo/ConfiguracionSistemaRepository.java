package com.pos.empresas.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.empresas.domain.ConfiguracionSistema;

public interface ConfiguracionSistemaRepository extends JpaRepository<ConfiguracionSistema, Integer> {

	Optional<ConfiguracionSistema> findByEmpresa_IdEmpresa(Integer idEmpresa);
}
