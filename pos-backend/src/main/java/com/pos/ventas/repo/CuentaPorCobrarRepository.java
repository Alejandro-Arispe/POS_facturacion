package com.pos.ventas.repo;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pos.ventas.domain.CuentaPorCobrar;

public interface CuentaPorCobrarRepository extends JpaRepository<CuentaPorCobrar, Integer> {

    List<CuentaPorCobrar> findByVenta_IdVenta(Integer idVenta);

    List<CuentaPorCobrar> findByVenta_IdCliente(Integer idCliente);

    @Query("SELECT COALESCE(SUM(c.saldoPendiente), 0) FROM CuentaPorCobrar c WHERE c.venta.idCliente = :idCliente AND UPPER(c.estado) <> 'PAGADA'")
    BigDecimal sumSaldoPendienteByCliente(@Param("idCliente") Integer idCliente);

    @Query("SELECT c.venta.idCliente, COALESCE(SUM(c.saldoPendiente), 0) FROM CuentaPorCobrar c WHERE UPPER(c.estado) <> 'PAGADA' AND c.venta.idCliente IS NOT NULL GROUP BY c.venta.idCliente")
    List<Object[]> findSaldosPendientesPorCliente();
}
