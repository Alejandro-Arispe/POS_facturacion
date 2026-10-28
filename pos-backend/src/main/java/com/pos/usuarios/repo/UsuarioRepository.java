package com.pos.usuarios.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.usuarios.domain.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

	Optional<Usuario> findByEmailIgnoreCase(String email);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from Usuario u where u.idUsuario=:id")
    java.util.Optional<Usuario> lockById(@org.springframework.data.repository.query.Param("id") Integer id);
}
