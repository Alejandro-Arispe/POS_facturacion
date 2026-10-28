package com.pos.common.access;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pos.common.exception.ApiException;
import com.pos.usuarios.domain.*;
import com.pos.usuarios.repo.*;

@Service
@Transactional(readOnly = true)
public class RbacResolver {
    public record Grants(Map<String, Permiso> permissions, Set<Integer> delegable, List<UsuarioRol> assignments) {
        public boolean allows(String code) {
            return permissions.containsKey(code);
        }
    }
    private final com.pos.empresas.repo.SucursalRepository branches;
    private final UsuarioRolRepository roles;
    private final RolPermisoRepository grants;
    private final RequestReadCache cache;
    public RbacResolver(UsuarioRolRepository roles, RolPermisoRepository grants, com.pos.empresas.repo.SucursalRepository branches, RequestReadCache cache) {
        this.cache=cache;
        this.roles=roles;
        this.grants=grants;
        this.branches=branches;
    }
    private List<UsuarioRol> activeAssignments(Usuario user,Integer company) {
        return cache.read("assignments:"+user.getIdUsuario()+":"+company, () -> roles.findAutorizaciones(user.getIdUsuario(),company));
    }
    private boolean branchExists(Integer company,Integer branch) {
        return cache.read("branch:"+company+":"+branch, () -> branches.findByIdSucursalAndEmpresa_IdEmpresa(branch,company).isPresent());
    }
    public static boolean companyAdministrator(UsuarioRol a) {
        return Boolean.TRUE.equals(a.getRol().getProtegido()) && a.getRol().getIdEmpresa() == null && "SISTEMA".equals(a.getRol().getTipo())
        && "ADMINISTRADOR".equals(a.getRol().getNombre());
    }
    public List<UsuarioRol> assignments(Usuario user, Integer company, Integer branch) {
        if (user == null || !Boolean.TRUE.equals(user.getActivo())) throw ApiException.unauthorized("Usuario no autorizado o inactivo.");
        if(branch!=null && !branchExists(company,branch)) throw ApiException.notFound("Sucursal no encontrada para esta empresa.");
        List<UsuarioRol> all = activeAssignments(user,company);
        if (all.isEmpty()) throw ApiException.notFound("Empresa no encontrada para este usuario.");
        List<UsuarioRol> scoped = all.stream().filter(a -> branch == null || a.getIdSucursal() == null
        || branch.equals(a.getIdSucursal()) || companyAdministrator(a)).toList();
        if (scoped.isEmpty()) throw ApiException.notFound("Sucursal no encontrada para este usuario.");
        return scoped;
    }
    public Grants resolve(Usuario user, Integer company, Integer branch) {
        List<UsuarioRol> scoped = assignments(user, company, branch);
        return combine(scoped);
    }
    public Grants resolveCompany(Usuario user,Integer company) {
        return combine(assignments(user,company,null).stream().filter(a -> a.getIdSucursal()==null || companyAdministrator(a)).toList());
    }
    private Grants combine(List<UsuarioRol> scoped) {
        if(scoped.isEmpty()) return new Grants(Map.of(),Set.of(),List.of());
        Set<Integer> ids = new LinkedHashSet<>();
        scoped.forEach(a -> ids.add(a.getRol().getIdRol()));
        Map<String,Permiso> effective = new LinkedHashMap<>();
        Set<Integer> delegable = new LinkedHashSet<>();
        for (RolPermiso row : cache.read("grants:"+ids, () -> grants.findConcesiones(ids))) {
            Permiso p = row.getPermiso();
            String code = p.getCodigoCanonico();
            if (code == null || !PermissionCodes.isCanonical(code)) continue;
            effective.putIfAbsent(code, p);
            if (Boolean.TRUE.equals(row.getPuedeDelegar())) delegable.add(p.getIdPermiso());
        }
        return new Grants(Collections.unmodifiableMap(effective), Collections.unmodifiableSet(delegable), scoped);
    }
    /** Non-throwing checks for filtering lists; a rejected row must not mark the enclosing transaction rollback-only. */
    public boolean hasBranchAccess(Usuario user,Integer company,Integer branch) {
        if(user==null || !Boolean.TRUE.equals(user.getActivo())) return false;
        if(branch!=null && !branchExists(company,branch)) return false;
        return activeAssignments(user,company).stream().anyMatch(a -> branch==null || a.getIdSucursal()==null || branch.equals(a.getIdSucursal()) || companyAdministrator(a));
    }
    public boolean allowsInBranch(Usuario user,Integer company,Integer branch,String code) {
        if(!hasBranchAccess(user,company,branch)) return false;
        return combine(activeAssignments(user,company).stream().filter(a -> branch==null || a.getIdSucursal()==null || branch.equals(a.getIdSucursal()) || companyAdministrator(a)).toList()).allows(code);
    }

    public void require(Usuario user, Integer company, Integer branch, String code) {
        if (!resolve(user, company, branch).allows(code)) throw ApiException.forbidden("No tienes el permiso '" + code + "' requerido para esta acción.");
    }
    public boolean field(Grants g, String resource, String action, String field) {
        return g.allows(resource + ":" + action) && g.allows(resource + ":" + action + ":" + field);
    }
}
