package com.pos.ventas.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pos.ventas.domain.DevolucionDetalle;

public interface DevolucionDetalleRepository extends JpaRepository<DevolucionDetalle, Integer> {

    List<DevolucionDetalle> findByDevolucion_IdDevolucion(Integer idDevolucion);

    /** Unidades ya devueltas por producto de una venta: [idProducto, cantidad]. */
    @Query("""
            select d.idProducto, sum(d.cantidad) from DevolucionDetalle d
            where d.devolucion.venta.idVenta = :idVenta group by d.idProducto
            """)
    List<Object[]> devueltoPorProducto(@Param("idVenta") Integer idVenta);
}
