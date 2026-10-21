package com.pos.ventas.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.ventas.domain.Venta;

public interface VentaRepository extends JpaRepository<Venta, Integer> {

    List<Venta> findByIdCajaSesion(Integer idCajaSesion);

    List<Venta> findByIdCajaSesionOrderByFechaDesc(Integer idCajaSesion);

    List<Venta> findByIdCliente(Integer idCliente);

    List<Venta> findByIdUsuario(Integer idUsuario);

    List<Venta> findByIdPedido(Integer idPedido);
}
