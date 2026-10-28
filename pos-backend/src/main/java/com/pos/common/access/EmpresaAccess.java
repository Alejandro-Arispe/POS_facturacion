package com.pos.common.access;


import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pos.common.exception.ApiException;
import com.pos.empresas.domain.Empresa;
import com.pos.empresas.domain.Sucursal;
import com.pos.empresas.repo.EmpresaRepository;
import com.pos.empresas.repo.SucursalRepository;
import com.pos.usuarios.domain.Usuario;

@Component
public class EmpresaAccess {

    private final EmpresaRepository empresaRepository;
    private final SucursalRepository sucursalRepository;
    private final com.pos.empresas.service.PlanPolicy planPolicy;
    private final RbacResolver rbac;

    public EmpresaAccess(
            EmpresaRepository empresaRepository,
            SucursalRepository sucursalRepository,
            com.pos.empresas.service.PlanPolicy planPolicy, RbacResolver rbac) {
        this.rbac = rbac;
        this.empresaRepository = empresaRepository;
        this.sucursalRepository = sucursalRepository;
        this.planPolicy = planPolicy;
    }

    public void requireUsuarioActivo(Usuario usuario) {
        if (usuario == null || !Boolean.TRUE.equals(usuario.getActivo())) {
            throw ApiException.unauthorized("Usuario no autorizado o inactivo.");
        }
    }

    @Transactional(readOnly = true)
    public Empresa requireEmpresa(Usuario usuario, Integer idEmpresa) {
        Empresa empresa = requireEmpresaSinPlan(usuario, idEmpresa);
        planPolicy.requireActive(idEmpresa);
        return empresa;
    }

    @Transactional(readOnly = true)
    public Empresa requireEmpresaSinPlan(Usuario usuario, Integer idEmpresa) {
        requireUsuarioActivo(usuario);
        Empresa empresa = empresaRepository.findById(idEmpresa)
                .orElseThrow(() -> ApiException.notFound("Empresa no encontrada."));
        rbac.assignments(usuario, idEmpresa, null);
        if (!Boolean.TRUE.equals(empresa.getActivo())) {
            throw ApiException.forbidden("La empresa fue suspendida por el administrador del sistema.");
        }
        return empresa;
    }

    public void requireProductCapacity(Integer idEmpresa) { planPolicy.requireProductCapacity(idEmpresa); }
    public void requireCashCapacity(Integer idEmpresa, Integer idSucursal) { planPolicy.requireCashCapacity(idEmpresa, idSucursal); }
    public com.pos.empresas.service.PlanPolicy.Benefits benefits(Integer idEmpresa) {
        return planPolicy.benefits(planPolicy.requireActive(idEmpresa).getPlan());
    }
    public void requirePlanFeature(Usuario usuario, Integer idEmpresa, String feature) {
        requireEmpresa(usuario, idEmpresa);
        var benefits = benefits(idEmpresa);
        boolean allowed = switch (feature) {
            case "pedidos" -> benefits.pedidos();
            case "predicciones", "reportes_avanzados" -> benefits.predicciones();
            case "reportes_estandar" -> !benefits.codigo().equals("gratuito");
            default -> true;
        };
        if (!allowed) throw ApiException.forbidden("Esta función requiere un plan superior. Puedes cambiarlo desde Mis empresas.");
    }

    @Transactional(readOnly = true)
    public Sucursal requireSucursal(Usuario usuario, Integer idEmpresa, Integer idSucursal) {
        requireEmpresa(usuario, idEmpresa);
        rbac.assignments(usuario, idEmpresa, idSucursal);
        return sucursalRepository.findByIdSucursalAndEmpresa_IdEmpresa(idSucursal, idEmpresa)
                .orElseThrow(() -> ApiException.notFound("Sucursal no encontrada para esta empresa."));
    }

    @Transactional(readOnly = true)
    public void requirePermiso(Usuario usuario, Integer idEmpresa, String codigo) {
        requireEmpresa(usuario, idEmpresa);
        if (codigo.startsWith("order:")) requirePlanFeature(usuario, idEmpresa, "pedidos");
        rbac.require(usuario, idEmpresa, null, codigo);
    }

    public void requirePermiso(Usuario usuario, Integer empresa, Integer sucursal, String codigo) {
        requireSucursal(usuario, empresa, sucursal);
        if (codigo.startsWith("order:")) requirePlanFeature(usuario, empresa, "pedidos");
        rbac.require(usuario, empresa, sucursal, codigo);
    }
    public boolean canAccess(Usuario user,Integer company,Integer branch,String feature) {
        return empresaRepository.findById(company).map(e -> Boolean.TRUE.equals(e.getActivo())).orElse(false)
            && rbac.hasBranchAccess(user,company,branch) && planPolicy.permits(company,feature);
    }
    public boolean canPermiso(Usuario user,Integer company,Integer branch,String code) {
        return canAccess(user,company,branch,code.startsWith("order:")?"pedidos":"")
            && rbac.allowsInBranch(user,company,branch,code);
    }

    public boolean canCompanyPermission(Usuario user,Integer company,String code) {
        return canAccess(user,company,null,"") && rbac.resolveCompany(user,company).allows(code);
    }
    public void requireCompanyPermission(Usuario usuario,Integer company,String code) {
        requireEmpresa(usuario,company);
        if(!rbac.resolveCompany(usuario,company).allows(code)) throw ApiException.forbidden("Se requiere el permiso de empresa '"+code+"'.");
    }
}
