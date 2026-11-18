package com.pos.ventas.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pos.ventas.domain.Factura;

public interface FacturaRepository extends JpaRepository<Factura, Integer> {

    Optional<Factura> findByVenta_IdVenta(Integer idVenta);

    List<Factura> findByVenta_IdClienteInOrderByFechaEmisionDesc(java.util.Collection<Integer> idsCliente);

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

    @Query("select f.idFactura from Factura f where f.estadoSiat = :estado order by f.idFactura")
    List<Integer> findIdsByEstadoSiat(@Param("estado") String estado);

    @Query("""
            select f.idFactura from Factura f
            where f.estadoSiat = 'VALIDADA' and f.correoEstado = 'PENDIENTE'
              and coalesce(f.correoIntentos, 0) < :max
              and (f.correoUltimoIntento is null or f.correoUltimoIntento < :limite)
            order by f.idFactura
            """)
    List<Integer> findIdsCorreoPendiente(@Param("max") int max, @Param("limite") java.time.LocalDateTime limite);

    @Query("select coalesce(max(f.numeroFactura), 0) from Factura f where f.nitEmisor = :nitEmisor")
    Integer maxNumeroPorEmisor(@Param("nitEmisor") String nitEmisor);
}
