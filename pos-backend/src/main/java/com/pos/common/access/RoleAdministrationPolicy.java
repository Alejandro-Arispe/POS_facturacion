package com.pos.common.access;

import java.util.*;
import org.springframework.stereotype.Service;
import com.pos.common.exception.ApiException;
import com.pos.empresas.domain.Empresa;
import com.pos.usuarios.domain.*;
import com.pos.usuarios.repo.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
public class RoleAdministrationPolicy {
    private final RbacResolver resolver;
    private final RolPermisoRepository grants;
    private final UsuarioRolRepository assignments;
    private final EntityManager em;
    public RoleAdministrationPolicy(RbacResolver resolver,RolPermisoRepository grants,UsuarioRolRepository assignments,EntityManager em) {
        this.resolver=resolver;
        this.grants=grants;
        this.assignments=assignments;
        this.em=em;
    }
    public void lock(Integer company) {
        if(em.find(Empresa.class,company,LockModeType.PESSIMISTIC_WRITE)==null) throw ApiException.notFound("Empresa no encontrada.");
        // Call at the start of a mutation, before changing entities. The database lock survives
        // clear(); discard pre-lock L1 snapshots of roles/grants loaded by the initial guard.
        em.clear();
    }
    /** Reload after acquiring the tenant lock: another editor may have committed while we waited. */
    public Rol refresh(Rol role) {
        Rol current=em.find(Rol.class,role.getIdRol());
        if(current==null) throw ApiException.notFound("Rol no encontrado.");
        return current;
    }
    public void mutable(Rol role) {
        if(role.getIdEmpresa()==null || Boolean.TRUE.equals(role.getProtegido()) || "SISTEMA".equals(role.getTipo()))
        throw ApiException.forbidden("Los roles protegidos se administran fuera de los roles de empresa.");
    }
    public RbacResolver.Grants companyScope(Usuario actor,Integer company) {
        var g=resolver.resolveCompany(actor,company);
        if(g.assignments().stream().noneMatch(a->a.getIdSucursal()==null || RbacResolver.companyAdministrator(a)))
        throw ApiException.forbidden("Se requiere un ambito de empresa.");
        return g;
    }
    public RbacResolver.Grants companyManager(Usuario actor,Integer company,String action) {
        if(!resolver.resolveCompany(actor,company).allows("role:"+action)) throw ApiException.forbidden("Se requiere autorización administrativa de empresa.");
        var g=resolver.resolveCompany(actor,company);
        if(g.assignments().stream().noneMatch(a->a.getIdSucursal()==null || RbacResolver.companyAdministrator(a)))
        throw ApiException.forbidden("Administrar un rol compartido requiere un ámbito de empresa.");
        return g;
    }
    public void field(RbacResolver.Grants actor,String action,String field) {
        if(!resolver.field(actor,"role",action,field)) throw ApiException.forbidden("No puedes modificar el atributo del rol: "+field);
    }
    public void grants(RbacResolver.Grants actor,Collection<Integer> desired,Collection<Integer> delegable) {
        Set<Integer> owned=new HashSet<>();
        actor.permissions().values().forEach(p->owned.add(p.getIdPermiso()));
        if(!owned.containsAll(desired) || !actor.delegable().containsAll(desired) || !desired.containsAll(delegable))
        throw ApiException.forbidden("Solo puedes conceder permisos que posees y puedes delegar.");
    }
    public void manageable(RbacResolver.Grants actor,Rol target) {
        List<RolPermiso> current=grants.findConcesiones(List.of(target.getIdRol()));
        grants(actor,current.stream().map(x->x.getPermiso().getIdPermiso()).toList(),List.of());
    }
    public void assign(Usuario actor,Integer company,Usuario target,Rol role,List<Integer> branches) {
        if(branches==null || branches.isEmpty() || branches.stream().anyMatch(Objects::isNull)) throw ApiException.badRequest("Selecciona al menos una sucursal.");
        if(!Boolean.TRUE.equals(role.getActivo()) || (role.getIdEmpresa()!=null && !company.equals(role.getIdEmpresa()))) throw ApiException.notFound("Rol no encontrado.");
        if(Boolean.TRUE.equals(target.getSuperAdmin())) throw ApiException.forbidden("La identidad de plataforma no se administra desde una empresa.");
        if(role.getIdEmpresa()==null && !"EMPLEADO".equals(role.getNombre())) throw ApiException.forbidden("Este rol protegido no se puede asignar al personal.");
        for(Integer branch:branches) {
            resolver.require(actor,company,branch,"role:assign");
            resolver.require(actor,company,branch,"user:update");
            manageable(resolver.resolve(actor,company,branch),role);
        }
    }
    public void editAssignments(Usuario actor,Integer company,Usuario target) {
        for(UsuarioRol row:assignments.findByUsuario_IdUsuarioAndIdEmpresa(target.getIdUsuario(),company)) {
            if(!Boolean.TRUE.equals(row.getActivo())) continue;
            if(row.getRol().getIdEmpresa()==null && "CLIENTE".equals(row.getRol().getNombre())) continue;
            if(RbacResolver.companyAdministrator(row)) throw ApiException.forbidden("No puedes retirar ni reemplazar al administrador propietario desde personal.");
            if(row.getIdSucursal()==null && resolver.assignments(actor,company,null).stream().noneMatch(a->a.getIdSucursal()==null || RbacResolver.companyAdministrator(a))) throw ApiException.forbidden("La asignación tiene ámbito de empresa.");
            resolver.require(actor,company,row.getIdSucursal(),"role:assign");
            manageable(resolver.resolve(actor,company,row.getIdSucursal()),row.getRol());
        }
    }
}
