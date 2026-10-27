package com.pos.usuarios.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pos.common.access.*;
import com.pos.common.exception.ApiException;
import com.pos.common.security.SecurityUtils;
import com.pos.usuarios.domain.*;
import com.pos.usuarios.dto.*;
import com.pos.usuarios.repo.*;

@Service
@Transactional(readOnly=true)
public class RolService {
    private final RolRepository roles;
    private final PermisoRepository permissions;
    private final RolPermisoRepository grants;
    private final EmpresaAccess access;
    private final RbacResolver resolver;
    private final RoleAdministrationPolicy policy;
    private final RbacAudit audit;
    public RolService(RolRepository roles,PermisoRepository permissions,RolPermisoRepository grants,EmpresaAccess access,RbacResolver resolver,RoleAdministrationPolicy policy,RbacAudit audit) {
        this.roles=roles;
        this.permissions=permissions;
        this.grants=grants;
        this.access=access;
        this.resolver=resolver;
        this.policy=policy;
        this.audit=audit;
    }
    public List<RolResponse> listarPorEmpresa(Integer company) {
        access.requirePermiso(SecurityUtils.currentUser(),company,"role:read");
        var actor=resolver.resolveCompany(SecurityUtils.currentUser(),company);
        return roles.findVisiblesPorEmpresa(company).stream()
        .sorted(Comparator.comparing(Rol::getIdRol)).map(r->RolResponse.from(r,administrable(actor,r))).toList();
    }
    public Map<String,Boolean> capacidades(Integer company) {
        access.requirePermiso(SecurityUtils.currentUser(),company,"role:read");
        var actor=resolver.resolveCompany(SecurityUtils.currentUser(),company);
        boolean scope=actor.assignments().stream().anyMatch(a->a.getIdSucursal()==null || RbacResolver.companyAdministrator(a));
        return Map.of("crear",scope && actor.allows("role:create") && actor.allows("role:create:nombre") && actor.allows("role:create:permissions"),"editar",scope && actor.allows("role:update"));
    }
    private boolean administrable(RbacResolver.Grants actor,Rol target) {
        return target.getIdEmpresa()!=null && !Boolean.TRUE.equals(target.getProtegido()) && !"SISTEMA".equals(target.getTipo())
                && actor.allows("role:update") && actor.assignments().stream().anyMatch(a->a.getIdSucursal()==null || RbacResolver.companyAdministrator(a))
                && grants.findConcesiones(List.of(target.getIdRol())).stream().allMatch(g->actor.delegable().contains(g.getPermiso().getIdPermiso()));
    }
    public List<Integer> delegables(Integer company) {
        access.requirePermiso(SecurityUtils.currentUser(),company,"role:read");
        return resolver.resolve(SecurityUtils.currentUser(),company,null).delegable().stream().sorted().toList();
    }
    public List<RolResponse> asignables(Integer company) {
        access.requirePermiso(SecurityUtils.currentUser(),company,"role:assign");
        var actor=resolver.resolveCompany(SecurityUtils.currentUser(),company);
        return roles.findVisiblesPorEmpresa(company).stream().filter(r->Boolean.TRUE.equals(r.getActivo()) && (company.equals(r.getIdEmpresa()) || "EMPLEADO".equals(r.getNombre())))
        .filter(r->grants.findConcesiones(List.of(r.getIdRol())).stream().allMatch(g->actor.delegable().contains(g.getPermiso().getIdPermiso())))
        .map(RolResponse::from).toList();
    }
    public Rol requireReadable(Integer id) {
        Rol role=roles.findById(id).orElseThrow(()->ApiException.notFound("Rol no encontrado."));
        if(role.getIdEmpresa()==null) throw ApiException.forbidden("Consulta la plantilla protegida en el listado de tu empresa.");
        access.requirePermiso(SecurityUtils.currentUser(),role.getIdEmpresa(),"role:read");
        return role;
    }
    public RolDetalleResponse obtenerPorId(Integer id) {
        Rol r=requireReadable(id);
        return detail(r,resolver.resolve(SecurityUtils.currentUser(),r.getIdEmpresa(),null).allows("role:read:permissions"));
    }
    @Transactional
    public RolCreateResponse crear(Integer company,RolCreateRequest req) {
        if(req.campos()!=null) throw ApiException.badRequest("El formato campos fue retirado. Usa permiso_ids canónicos.");
        Usuario actor=SecurityUtils.currentUser();
        access.requirePermiso(actor,company,"role:create");
        policy.lock(company);
        access.requirePermiso(actor,company,"role:create");
        var capabilities=policy.companyManager(actor,company,"create");
        policy.field(capabilities,"create","nombre");
        if(req.descripcion()!=null) policy.field(capabilities,"create","descripcion");
        if(req.activo()!=null) policy.field(capabilities,"create","activo");
        policy.field(capabilities,"create","permissions");
        String name=name(company,null,req.nombre());
        Rol r=new Rol();
        r.setNombre(name);
        r.setDescripcion(description(req.descripcion()==null?name:req.descripcion()));
        r.setActivo(req.activo()==null || req.activo());
        r.setTipo("CUSTOM");
        r.setIdEmpresa(company);
        Set<Integer> desired=unique(req.permisoIds());
        Set<Integer> delegate=unique(req.delegableIds());
        validate(capabilities,desired,delegate);
        roles.saveAndFlush(r);
        write(r,desired,delegate);
        audit.record(actor,company,"role:create","rol="+r.getIdRol()+"; permisos="+desired+"; delegables="+delegate);
        return new RolCreateResponse(RolResponse.from(r,true),List.copyOf(desired));
    }
    @Transactional
    public RolDetalleResponse editar(Integer id,RolUpdateRequest req) {
        if(req.campos()!=null) throw ApiException.badRequest("El formato campos fue retirado. Usa permiso_ids canónicos.");
        Usuario actor=SecurityUtils.currentUser();
        Rol r=requireReadable(id);
        policy.mutable(r);
        policy.lock(r.getIdEmpresa());
        r=policy.refresh(r);
        access.requireEmpresa(actor,r.getIdEmpresa());
        var capabilities=policy.companyManager(actor,r.getIdEmpresa(),"update");
        policy.manageable(capabilities,r);
        if(req.nombre()!=null) {
            policy.field(capabilities,"update","nombre");
            r.setNombre(name(r.getIdEmpresa(),id,req.nombre()));
        }
        if(req.descripcion()!=null) {
            policy.field(capabilities,"update","descripcion");
            r.setDescripcion(description(req.descripcion()));
        }
        if(req.activo()!=null) {
            policy.field(capabilities,"update","activo");
            r.setActivo(req.activo());
        }
        if(req.permisoIds()!=null || req.delegableIds()!=null || req.campos()!=null) {
            policy.field(capabilities,"update","permissions");
            var current=grants.findByRol_IdRol(id);
            Set<Integer> desired=req.permisoIds()==null?new LinkedHashSet<>(current.stream().filter(g->Boolean.TRUE.equals(g.getActivo())).map(g->g.getPermiso().getIdPermiso()).toList()):unique(req.permisoIds());
            Set<Integer> delegate=req.delegableIds()==null?new LinkedHashSet<>(current.stream().filter(g->Boolean.TRUE.equals(g.getActivo()) && Boolean.TRUE.equals(g.getPuedeDelegar())).map(g->g.getPermiso().getIdPermiso()).toList()):unique(req.delegableIds());
            if(req.delegableIds()==null) delegate.retainAll(desired);
            validate(capabilities,desired,delegate);
            write(r,desired,delegate);
        }
        roles.saveAndFlush(r);
        audit.record(actor,r.getIdEmpresa(),"role:update","rol="+id+"; activo="+r.getActivo()+"; atributos="+List.of(req.nombre()!=null,req.descripcion()!=null,req.activo()!=null,req.permisoIds()!=null || req.campos()!=null));
        return detail(r,capabilities.allows("role:read:permissions"));
    }
    private void validate(RbacResolver.Grants actor,Set<Integer> desired,Set<Integer> delegate) {
        for(Integer id:desired) if(id==null || permissions.findById(id).filter(p->Boolean.TRUE.equals(p.getActivo()) && PermissionCodes.isCanonical(p.getCodigoCanonico())).isEmpty()) throw ApiException.badRequest("Permiso inexistente o inactivo: "+id);
        policy.grants(actor,desired,delegate);
    }
    private void write(Rol role,Set<Integer> desired,Set<Integer> delegate) {
        Map<Integer,RolPermiso> current=new HashMap<>();
        grants.findByRol_IdRol(role.getIdRol()).forEach(g->current.put(g.getPermiso().getIdPermiso(),g));
        for(Integer id:desired) if(!current.containsKey(id)) {
            RolPermiso g=new RolPermiso();
            g.setRol(role);
            g.setPermiso(permissions.getReferenceById(id));
            current.put(id,g);
        }
        current.forEach((id,g)->{
            g.setActivo(desired.contains(id));
            g.setPuedeDelegar(desired.contains(id) && delegate.contains(id));
        });
        grants.saveAllAndFlush(current.values());
    }
    private String name(Integer company,Integer id,String input) {
        String n=input==null?"":input.strip();
        String norm=n.toUpperCase(Locale.ROOT);
        if(n.isBlank() || n.length()>100 || Set.of("ADMINISTRADOR","SUPERADMIN","SUPER_ADMIN","SUPERADMINISTRADOR","CLIENTE","EMPLEADO").contains(norm)) throw ApiException.badRequest("Nombre vacío, reservado o demasiado largo.");
        if(roles.findByIdEmpresa(company).stream().anyMatch(r->!Objects.equals(id,r.getIdRol()) && r.getNombre().strip().equalsIgnoreCase(n))) throw ApiException.conflict("Ya existe un rol con ese nombre en la empresa.","Ya existe un rol con ese nombre en la empresa.");
        return n;
    }
    private String description(String d){
        if(d==null || d.length()>255) throw ApiException.badRequest("Descripción inválida (máximo 255 caracteres).");
        return d.strip();
    }
    private Set<Integer> unique(List<Integer> ids){
        if(ids==null)return new LinkedHashSet<>();
        if(ids.stream().anyMatch(Objects::isNull))throw ApiException.badRequest("ID de permiso inválido.");
        return new LinkedHashSet<>(ids);
    }
    private RolDetalleResponse detail(Rol r){
        return detail(r,true);
    }
    private RolDetalleResponse detail(Rol r,boolean readGrants){
        return new RolDetalleResponse(r.getIdRol(),r.getNombre(),r.getIdEmpresa(),r.getTipo(),r.getDescripcion(),r.getActivo(),readGrants?grants.findByRol_IdRol(r.getIdRol()).stream().map(RolPermisoResponse::from).toList():null);
    }
}
