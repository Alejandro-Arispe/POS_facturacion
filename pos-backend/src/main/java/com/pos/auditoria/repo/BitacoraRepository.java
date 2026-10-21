package com.pos.auditoria.repo;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.pos.auditoria.domain.Bitacora;

public interface BitacoraRepository extends JpaRepository<Bitacora, Long>, JpaSpecificationExecutor<Bitacora> {

	Optional<Bitacora> findTopByOrderByIdBitacoraDesc();

	long countByFechaAfter(LocalDateTime fecha);
}
