package com.pos.notifications.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.notifications.domain.NotificacionHistorial;

public interface NotificacionHistorialRepository extends JpaRepository<NotificacionHistorial, Integer> {

    List<NotificacionHistorial> findByIdEmpresaOrderByFechaDesc(Integer idEmpresa);
}
