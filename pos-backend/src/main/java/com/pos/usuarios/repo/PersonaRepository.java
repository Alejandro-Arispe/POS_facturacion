package com.pos.usuarios.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.usuarios.domain.Persona;

public interface PersonaRepository extends JpaRepository<Persona, Integer> {

	Optional<Persona> findByDocumento(String documento);
}
