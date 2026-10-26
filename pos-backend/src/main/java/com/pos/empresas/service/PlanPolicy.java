package com.pos.empresas.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pos.common.exception.ApiException;
import com.pos.empresas.domain.HistorialSuscripcion;
import com.pos.empresas.domain.Plan;
import com.pos.empresas.repo.HistorialSuscripcionRepository;
import com.pos.empresas.repo.PlanModuloRepository;
import jakarta.persistence.EntityManager;

/** Reglas del catálogo, compartidas por la página pública y las operaciones del POS. */
@Service
public class PlanPolicy {
    public record Benefits(String codigo, int maxUsuarios, int maxSucursales, int maxCajas,
            int maxProductos, String reportes, boolean pedidos, boolean predicciones) {}

    private final HistorialSuscripcionRepository subscriptions;
    private final PlanModuloRepository modules;
    private final EntityManager em;

    public PlanPolicy(HistorialSuscripcionRepository subscriptions, PlanModuloRepository modules, EntityManager em) {
        this.subscriptions = subscriptions;
        this.modules = modules;
        this.em = em;
    }

    public static Benefits defaults(Plan plan) {
        if (plan.getPrecio() != null && plan.getPrecio().signum() == 0) {
            return new Benefits("gratuito", 2, 1, 1, 100, "diario", false, false);
        }
        String name = plan.getNombre().toLowerCase(Locale.ROOT);
        if (name.equals("básico") || name.equals("basico")) {
            return new Benefits("basico", 5, 1, 2, 1000, "estandar", false, false);
        }
        return new Benefits("pro", 15, 3, 3, 10000, "avanzado", true, true);
    }

    @Transactional(readOnly = true)
    public Benefits benefits(Plan plan) {
        Benefits b = defaults(plan);
        List<String> config = modules.findByPlan_IdPlan(plan.getIdPlan()).stream()
                .map(m -> m.getConfiguracion() == null ? "" : m.getConfiguracion()).toList();
        return new Benefits(b.codigo(), limit(config, "max_usuarios", b.maxUsuarios()),
                limit(config, "max_sucursales", b.maxSucursales()), limit(config, "max_cajas", b.maxCajas()),
                limit(config, "max_productos", b.maxProductos()), b.reportes(), b.pedidos(), b.predicciones());
    }

    private int limit(List<String> configs, String key, int fallback) {
        Pattern pattern = Pattern.compile("[\"']?" + key + "[\"']?\\s*[:=]\\s*[\"']?(\\d+)", Pattern.CASE_INSENSITIVE);
        for (String config : configs) {
            var match = pattern.matcher(config);
            if (match.find()) return Integer.parseInt(match.group(1));
        }
        return fallback;
    }

    @Transactional(readOnly = true)
    public HistorialSuscripcion requireActive(Integer companyId) {
        return subscriptions.findActivasPorEmpresa(companyId, LocalDate.now()).stream().findFirst()
                .orElseThrow(() -> ApiException.forbidden("Activa un plan desde Mis empresas para acceder al POS."));
    }

    @Transactional
    public void requireProductCapacity(Integer companyId) {
        lockCompany(companyId);
        int max = benefits(requireActive(companyId).getPlan()).maxProductos();
        long current = em.createQuery("select count(p) from Producto p where p.idEmpresa = :id and p.activo = true", Long.class)
                .setParameter("id", companyId).getSingleResult();
        capacity(current, max, "productos activos");
    }

    @Transactional
    public void requireCashCapacity(Integer companyId, Integer branchId) {
        lockCompany(companyId);
        int max = benefits(requireActive(companyId).getPlan()).maxCajas();
        long current = em.createQuery("select count(c) from Caja c where c.sucursal.idSucursal = :id and c.activo = true", Long.class)
                .setParameter("id", branchId).getSingleResult();
        capacity(current, max, "cajas activas por sucursal");
    }

    public void lockCompany(Integer companyId) {
        em.find(com.pos.empresas.domain.Empresa.class, companyId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    private void capacity(long current, int max, String resource) {
        if (current >= max) throw ApiException.badRequest("Tu plan permite hasta " + max + " " + resource + ". Cambia de plan para ampliar el límite.");
    }
}
