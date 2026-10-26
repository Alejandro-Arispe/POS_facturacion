package com.pos.pagos.service;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pos.common.exception.ApiException;
import com.pos.empresas.domain.Empresa;
import com.pos.empresas.domain.HistorialSuscripcion;
import com.pos.empresas.repo.HistorialSuscripcionRepository;
import com.pos.pagos.domain.CompraPlan;
import com.pos.pagos.dto.PagoDtos;
import com.pos.pagos.repo.CompraPlanRepository;
import com.pos.usuarios.domain.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
public class CompraPlanService {
    private final CompraPlanRepository compras;
    private final HistorialSuscripcionRepository historial;
    private final EntityManager em;
    public CompraPlanService(CompraPlanRepository compras, HistorialSuscripcionRepository historial, EntityManager em) {
        this.compras = compras; this.historial = historial; this.em = em;
    }

    @Transactional(readOnly = true)
    public List<PagoDtos.CompraResponse> disponibles(Usuario usuario) {
        return compras.findByUsuario_IdUsuarioAndEmpresaIsNullAndEstadoOrderByIdCompraPlanAsc(usuario.getIdUsuario(), "pagado")
                .stream().map(this::response).toList();
    }

    @Transactional
    public void asignar(Usuario usuario, Integer idCompra, Empresa empresa) {
        CompraPlan compra = em.find(CompraPlan.class, idCompra, LockModeType.PESSIMISTIC_WRITE);
        if (compra == null || !compra.getUsuario().getIdUsuario().equals(usuario.getIdUsuario())) {
            throw ApiException.notFound("No se encontró el plan adquirido para esta cuenta.");
        }
        if (!"pagado".equals(compra.getEstado()) || compra.getEmpresa() != null) {
            throw ApiException.badRequest("El plan debe estar pagado y disponible para asociarlo a una empresa.");
        }
        HistorialSuscripcion subscription = new HistorialSuscripcion();
        subscription.setPlan(compra.getPlan()); subscription.setEmpresa(empresa);
        subscription.setEstado("activo"); subscription.setStripePaymentStatus("paid");
        subscription.setFechaInicio(LocalDate.now());
        // Los días de uso comienzan al configurar la empresa, no mientras se completa el alta.
        subscription.setFechaFin(compra.getPlan().getPrecio().signum() == 0 ? null : LocalDate.now().plusDays(30));
        historial.save(subscription);
        compra.setEmpresa(empresa); compras.save(compra);
    }

    public PagoDtos.CompraResponse response(CompraPlan compra) {
        var dto = new PagoDtos.CompraResponse();
        dto.setIdCompraPlan(compra.getIdCompraPlan()); dto.setIdPlan(compra.getPlan().getIdPlan());
        dto.setPlanNombre(compra.getPlan().getNombre()); dto.setEstado(compra.getEstado());
        dto.setGratuito(compra.getPlan().getPrecio().signum() == 0);
        dto.setIdEmpresa(compra.getEmpresa() == null ? null : compra.getEmpresa().getIdEmpresa());
        return dto;
    }
}
