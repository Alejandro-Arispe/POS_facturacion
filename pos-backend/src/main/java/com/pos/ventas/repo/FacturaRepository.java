package com.pos.ventas.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pos.ventas.domain.Factura;

public interface FacturaRepository extends JpaRepository<Factura, Integer> {

    Optional<Factura> findByVenta_IdVenta(Integer idVenta);

    Optional<Factura> findByCuf(String cuf);

    @Query("""
            select f from Factura f
            join f.venta v
            where exists (
                select 1 from CajaSesion cs
                join cs.caja c
                join c.sucursal s
                where cs.idCajaSesion = v.idCajaSesion
                  and s.empresa.idEmpresa = :idEmpresa
            )
            order by f.fechaEmision desc
            """)
    List<Factura> findByIdEmpresa(@Param("idEmpresa") Integer idEmpresa);
}
