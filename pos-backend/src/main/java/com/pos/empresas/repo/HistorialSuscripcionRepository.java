package com.pos.empresas.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pos.empresas.domain.HistorialSuscripcion;

public interface HistorialSuscripcionRepository extends JpaRepository<HistorialSuscripcion, Integer> {

	Optional<HistorialSuscripcion> findByStripeSessionId(String stripeSessionId);

	Optional<HistorialSuscripcion> findByStripePaymentIntentId(String stripePaymentIntentId);

	List<HistorialSuscripcion> findByEmpresa_IdEmpresa(Integer idEmpresa);

	List<HistorialSuscripcion> findByEmpresa_IdEmpresaAndEstado(Integer idEmpresa, String estado);

	@Query("""
			select hs from HistorialSuscripcion hs
			join fetch hs.plan
			where hs.empresa.idEmpresa = :idEmpresa
			  and lower(hs.estado) in ('activo', 'activa')
			  and (hs.fechaFin is null or hs.fechaFin >= :hoy)
			order by hs.fechaFin desc
			""")
	List<HistorialSuscripcion> findActivasPorEmpresa(
			@Param("idEmpresa") Integer idEmpresa,
			@Param("hoy") LocalDate hoy);
}
