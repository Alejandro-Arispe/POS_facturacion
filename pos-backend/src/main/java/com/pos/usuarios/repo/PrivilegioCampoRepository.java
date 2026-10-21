package com.pos.usuarios.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.usuarios.domain.PrivilegioCampo;

public interface PrivilegioCampoRepository extends JpaRepository<PrivilegioCampo, Integer> {

    List<PrivilegioCampo> findByIdRolAndFormulario(Integer idRol, String formulario);

    Optional<PrivilegioCampo> findByIdRolAndFormularioAndCampo(Integer idRol, String formulario, String campo);
}
