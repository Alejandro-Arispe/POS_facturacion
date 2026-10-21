package com.pos.ventas.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.ventas.domain.PedidoClienteDetalle;

public interface PedidoClienteDetalleRepository extends JpaRepository<PedidoClienteDetalle, Integer> {

    List<PedidoClienteDetalle> findByPedido_IdPedido(Integer idPedido);
}
