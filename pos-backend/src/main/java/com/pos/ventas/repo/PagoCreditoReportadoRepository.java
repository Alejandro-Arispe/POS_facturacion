package com.pos.ventas.repo;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pos.ventas.domain.PagoCreditoReportado;

public interface PagoCreditoReportadoRepository extends JpaRepository<PagoCreditoReportado, Integer> {
    List<PagoCreditoReportado> findByIdCxcOrderByFechaReporteDesc(Integer idCxc);

    @Query("select coalesce(sum(p.monto), 0) from PagoCreditoReportado p where p.idCxc = :idCxc and p.estado = 'PENDIENTE'")
    BigDecimal sumPendienteByCxc(@Param("idCxc") Integer idCxc);
}
