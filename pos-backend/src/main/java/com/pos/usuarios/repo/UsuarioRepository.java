package com.pos.usuarios.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.usuarios.domain.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

	Optional<Usuario> findByEmailIgnoreCase(String email);
}
