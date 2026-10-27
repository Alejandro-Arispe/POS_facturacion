package com.pos.common.access;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pos.usuarios.domain.*;
import com.pos.usuarios.repo.*;
import jakarta.persistence.EntityManager;

/** Versioned, one-time data conversion. Never restores grants on later boots. */
@Service
public class RbacMigration {
    private final EntityManager em;
    private final PermisoRepository permisos;
    private final RolRepository roles;
    private final RolPermisoRepository grants;
    private final ModuloRepository modulos;
    private final PrivilegioCampoRepository campos;
    public RbacMigration(EntityManager em, PermisoRepository permisos, RolRepository roles,
    RolPermisoRepository grants, ModuloRepository modulos, PrivilegioCampoRepository campos) {
        this.em=em;
        this.permisos=permisos;
        this.roles=roles;
        this.grants=grants;
        this.modulos=modulos;
        this.campos=campos;
    }
    /** Acquire in the runner transaction BEFORE it creates catalogue rows. */
    @Transactional
    public void lockCatalog() {
        boolean postgres=em.unwrap(org.hibernate.Session.class).doReturningWork(connection -> "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()));
        if(postgres) em.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(77122708)").getResultList();
    }
    @Transactional
    public void migrate() {
        lockCatalog();
        em.createNativeQuery("CREATE TABLE IF NOT EXISTS rbac_migration (version INTEGER PRIMARY KEY, applied_at TIMESTAMP NOT NULL)").executeUpdate();
        // Repair mixed catalogues even when V1 was recorded by an older startup.
        normalizeCatalog();
        if (!em.createNativeQuery("SELECT version FROM rbac_migration WHERE version=1").getResultList().isEmpty()) { migrateProducts(); migrateCommercial(); return; }
        for (String action : List.of("create", "update")) {
            for (String field : List.of("nombre", "descripcion", "activo", "permissions")) add("role:"+action+":"+field, "USUARIOS");
        }
        add("role:read:permissions", "USUARIOS");
        add("role:assign", "USUARIOS");
        for (String action : List.of("read", "create", "update"))
        for (String field : List.of("costo", "precio")) add("product:"+action+":"+field,"INVENTARIO");
        add("product:delete", "INVENTARIO");
        permisos.flush();
        for (Rol r : roles.findAll()) {
            r.normalizeScope();
            if (r.getIdEmpresa()==null) r.setProtegido(true);
            // Merge historical duplicate grants restrictively; a revoked duplicate wins.
            Map<Integer,RolPermiso> byId=new LinkedHashMap<>();
            for (RolPermiso row : grants.findByRol_IdRol(r.getIdRol())) {
                RolPermiso previous=byId.putIfAbsent(row.getPermiso().getIdPermiso(),row);
                if (previous!=null) {
                    previous.setActivo(Boolean.TRUE.equals(previous.getActivo()) && Boolean.TRUE.equals(row.getActivo()));
                    previous.setPuedeDelegar(Boolean.TRUE.equals(previous.getPuedeDelegar()) && Boolean.TRUE.equals(row.getPuedeDelegar()));
                    grants.delete(row);
                }
            }
            grants.flush();
            boolean owner = r.getIdEmpresa()==null && "ADMINISTRADOR".equals(r.getNombre()) && "SISTEMA".equals(r.getTipo());
            Set<String> active=new HashSet<>();
            byId.values().stream().filter(x->Boolean.TRUE.equals(x.getActivo()) && Boolean.TRUE.equals(x.getPermiso().getActivo())).forEach(x->active.add(x.getPermiso().getCodigoCanonico()));
            for (String action : List.of("read","create","update")) {
                if (active.contains("role:"+action)) for(String field:action.equals("read")?List.of("permissions"):List.of("nombre","descripcion","activo","permissions")) grant(r,"role:"+action+":"+field,owner);
            }
            // Assigning business roles is a new administrative capability, initially reserved to owners.
            if(owner) grant(r,"role:assign",true);
            Map<String,String> old=new HashMap<>();
            campos.findByIdRolAndFormulario(r.getIdRol(),"producto").forEach(c->old.put(c.getCampo(),c.getEstado()));
            for (String field:List.of("costo","precio")) {
                String state=old.getOrDefault(field,"EDITABLE");
                if(!"OCULTO".equals(state) && active.contains("product:read")) grant(r,"product:read:"+field,owner);
                if("EDITABLE".equals(state)) for(String action:List.of("create","update"))
                if(active.contains("product:"+action)) grant(r,"product:"+action+":"+field,owner);
            }
            if(active.contains("product:update") && "EDITABLE".equals(old.getOrDefault("eliminar","EDITABLE"))) grant(r,"product:delete",owner);
            if(!"EDITABLE".equals(old.getOrDefault("editar","EDITABLE")))
            byId.values().stream().filter(x->"product:update".equals(x.getPermiso().getCodigoCanonico())).forEach(x->x.setActivo(false));
            if(owner) byId.values().forEach(x->x.setPuedeDelegar(Boolean.TRUE.equals(x.getActivo())));
        }
        em.flush();
        // Normalize nullable scopes without expression indexes, equally supported by PostgreSQL and H2.
        em.createNativeQuery("UPDATE usuario_rol SET sucursal_ambito=COALESCE(id_sucursal, -1)").executeUpdate();
        // Conflicting legacy names/assignments stop migration, rather than silently changing business data.
        em.createNativeQuery("CREATE UNIQUE INDEX IF NOT EXISTS ux_rbac_permission_code ON permiso(codigo_canonico)").executeUpdate();
        em.createNativeQuery("CREATE UNIQUE INDEX IF NOT EXISTS ux_rbac_role_name ON rol(ambito, nombre_normalizado)").executeUpdate();
        em.createNativeQuery("CREATE UNIQUE INDEX IF NOT EXISTS ux_rbac_grant ON rol_permiso(id_rol,id_permiso)").executeUpdate();
        em.createNativeQuery("CREATE UNIQUE INDEX IF NOT EXISTS ux_rbac_assignment ON usuario_rol(id_usuario,id_empresa,id_rol,sucursal_ambito)").executeUpdate();
        em.createNativeQuery("CREATE INDEX IF NOT EXISTS ix_rbac_context ON usuario_rol(id_usuario,id_empresa,activo)").executeUpdate();
        em.createNativeQuery("INSERT INTO rbac_migration(version,applied_at) VALUES (1,CURRENT_TIMESTAMP)").executeUpdate();
        migrateProducts();
        migrateCommercial();
    }
    private void migrateProducts() {
        if (!em.createNativeQuery("SELECT version FROM rbac_migration WHERE version=2").getResultList().isEmpty()) return;
        java.util.Set<String> added = new java.util.LinkedHashSet<>();
        for (String action : List.of("read","create","update")) for (String field : ProductFields.ALL) {
            String code="product:"+action+":"+field;
            if (permisos.findByCodigoCanonico(code).isEmpty()) { add(code,"INVENTARIO"); added.add(code); }
        }
        permisos.flush();
        for (Rol role : roles.findAll()) {
            for (RolPermiso base : grants.findByRol_IdRol(role.getIdRol())) {
                if (!Boolean.TRUE.equals(base.getActivo()) || !Boolean.TRUE.equals(base.getPermiso().getActivo())) continue;
                String code=base.getPermiso().getCodigoCanonico();
                if (code==null || !List.of("product:read","product:create","product:update").contains(code)) continue;
                for (String field : ProductFields.ALL) {
                    String fieldCode=code+":"+field;
                    // Never restore financial grants or any previously registered/revoked attribute grant.
                    if (added.contains(fieldCode) && !List.of("costo","precio").contains(field))
                        grant(role,fieldCode,Boolean.TRUE.equals(base.getPuedeDelegar()));
                }
            }
        }
        em.flush();
        em.createNativeQuery("INSERT INTO rbac_migration(version,applied_at) VALUES (2,CURRENT_TIMESTAMP)").executeUpdate();
    }
    private void migrateCommercial() {
        if (!em.createNativeQuery("SELECT version FROM rbac_migration WHERE version=3").getResultList().isEmpty()) return;
        em.createNativeQuery("UPDATE cliente SET id_empresa=(SELECT c.id_empresa FROM categoria_cliente c WHERE c.id_categoria_cliente=cliente.id_categoria_cliente) WHERE id_empresa IS NULL AND id_categoria_cliente IS NOT NULL").executeUpdate();
        em.createNativeQuery("UPDATE cliente SET id_empresa=(SELECT MIN(ur.id_empresa) FROM usuario_rol ur JOIN rol r ON r.id_rol=ur.id_rol WHERE ur.id_usuario=cliente.id_usuario AND ur.activo=true AND r.protegido=true AND r.tipo='SISTEMA' AND r.nombre='CLIENTE') WHERE id_empresa IS NULL AND (SELECT COUNT(DISTINCT ur.id_empresa) FROM usuario_rol ur JOIN rol r ON r.id_rol=ur.id_rol WHERE ur.id_usuario=cliente.id_usuario AND ur.activo=true AND r.protegido=true AND r.tipo='SISTEMA' AND r.nombre='CLIENTE')=1").executeUpdate();
        Map<String,String> sources=Map.of(
            "sale:create:precio_unitario","sale:create", "sale:create:descuento","sale:discount",
            "cash_movement:read","cash_register:read", "cash_movement:create","cash_register:update",
            "sale:collect","sale:create");
        Set<String> added=new HashSet<>();
        for(String code:sources.keySet()) {
            if(permisos.findByCodigoCanonico(code).isEmpty()) { add(code,code.startsWith("sale:")?"VENTAS":"CAJAS"); added.add(code); }
        }
        permisos.flush();
        for(Rol role:roles.findAll()) {
            List<RolPermiso> existing=grants.findByRol_IdRol(role.getIdRol());
            for(String code:added) {
                // Custom sale prices and debt collection are new powers, initially reserved to the protected owner role.
                boolean owner=role.getIdEmpresa()==null && Boolean.TRUE.equals(role.getProtegido()) && "SISTEMA".equals(role.getTipo()) && "ADMINISTRADOR".equals(role.getNombre());
                if((code.equals("sale:create:precio_unitario") || code.equals("sale:collect")) && !owner) continue;
                existing.stream().filter(g -> Boolean.TRUE.equals(g.getActivo()) && Boolean.TRUE.equals(g.getPermiso().getActivo()) && sources.get(code).equals(g.getPermiso().getCodigoCanonico()))
                    .findFirst().ifPresent(base -> grant(role,code,Boolean.TRUE.equals(base.getPuedeDelegar())));
            }
        }
        em.flush();
        em.createNativeQuery("INSERT INTO rbac_migration(version,applied_at) VALUES (3,CURRENT_TIMESTAMP)").executeUpdate();
    }

    private void normalizeCatalog() {
        Map<String,List<Permiso>> groups=new TreeMap<>();
        for(Permiso p:permisos.findAll()) {
            String physical=PermissionCodes.canonical(p.getCodigo());
            String stored=PermissionCodes.isCanonical(p.getCodigoCanonico())?p.getCodigoCanonico():"";
            if(!stored.isEmpty() && !physical.isEmpty() && !stored.equals(physical))
                throw new IllegalStateException("Códigos RBAC inconsistentes para el permiso #"+p.getIdPermiso());
            String code=stored.isEmpty()?physical:stored;
            if(!code.isEmpty()) groups.computeIfAbsent(code,ignored->new ArrayList<>()).add(p);
        }
        for(var entry:groups.entrySet()) {
            String code=entry.getKey();List<Permiso> group=entry.getValue();
            group.sort(Comparator.comparing(Permiso::getIdPermiso));
            // Keep the ID which already owns the unique canonical key; otherwise keep the oldest.
            Permiso keeper=group.stream().filter(p->code.equals(p.getCodigoCanonico())).findFirst().orElse(group.get(0));
            if(group.size()>1) mergePermissions(keeper,group);
            if(!code.equals(keeper.getCodigoCanonico()) || keeper.getRecurso()==null || keeper.getAccion()==null)
                metadata(keeper,code);
        }
        // Detect collisions here, before a later lookup/add triggers an unrelated auto-flush.
        permisos.flush();
    }
    private void mergePermissions(Permiso keeper,List<Permiso> group) {
        Map<Integer,List<RolPermiso>> perRole=new TreeMap<>();
        for(RolPermiso row:grants.findForPermissionMerge(group.stream().map(Permiso::getIdPermiso).toList()))
            perRole.computeIfAbsent(row.getRol().getIdRol(),ignored->new ArrayList<>()).add(row);
        List<RolPermiso> retained=new ArrayList<>(),removed=new ArrayList<>();
        for(List<RolPermiso> rows:perRole.values()) {
            RolPermiso chosen=rows.stream().filter(r->keeper.getIdPermiso().equals(r.getPermiso().getIdPermiso())).findFirst().orElse(rows.get(0));
            boolean active=rows.stream().allMatch(r->Boolean.TRUE.equals(r.getActivo()));
            boolean delegate=active && rows.stream().allMatch(r->Boolean.TRUE.equals(r.getPuedeDelegar()));
            chosen.setActivo(active);chosen.setPuedeDelegar(delegate);retained.add(chosen);
            rows.stream().filter(r->r!=chosen).forEach(removed::add);
        }
        // Remove conflicting grants BEFORE rewiring IDs, keeping the unique role/permission index.
        grants.deleteAll(removed);grants.flush();
        retained.forEach(row->row.setPermiso(keeper));grants.saveAllAndFlush(retained);
        keeper.setActivo(group.stream().allMatch(p->Boolean.TRUE.equals(p.getActivo())));
        permisos.deleteAll(group.stream().filter(p->p!=keeper).toList());permisos.flush();
    }

    private void metadata(Permiso p,String c) {
        String[] parts=c.split(":");
        p.setCodigoCanonico(c);
        p.setRecurso(parts[0]);
        p.setAccion(parts[1]);
        p.setAtributo(parts.length==3?parts[2]:null);
        if(p.getDescripcion()==null || p.getDescripcion().isBlank()) p.setDescripcion(p.getNombre());
    }
    private void add(String c,String module) {
        if(permisos.findByCodigoCanonico(c).isPresent()) return;
        Permiso p=new Permiso();
        p.setCodigo(c);
        p.setNombre(c.startsWith("product:") && c.split(":").length==3 ? ProductFields.LABELS.getOrDefault(c.split(":")[2],c.split(":")[2]) : label(c));
        p.setModulo(modulos.findByCodigoIgnoreCase(module).orElseThrow());
        metadata(p,c);
        permisos.save(p);
    }
    private String label(String code) {
        String commercial=switch(code) {
            case "sale:create:precio_unitario" -> "Establecer precio unitario de venta";
            case "sale:create:descuento" -> "Establecer descuento de venta";
            case "sale:collect" -> "Registrar cobros de crédito";
            case "cash_movement:read" -> "Ver movimientos de caja";
            case "cash_movement:create" -> "Registrar movimientos de caja";
            default -> null;
        };
        if(commercial!=null) return commercial;
        String[] p=code.split(":");
        String action=switch(p[1]) {
            case "read"->"Ver";
            case "create"->"Crear";
            case "update"->"Editar";
            case "assign"->"Asignar";
            default->"Eliminar";
        };
        String field=p.length==3?switch(p[2]){
            case "permissions"->"permisos";
            case "descripcion"->"descripción";
            default->p[2];
        }
        :"";
        return action+" "+(field.isEmpty()?"":field+" de ")+(p[0].equals("role")?"roles":"productos");
    }
    private void grant(Rol r,String code,boolean delegable) {
        Permiso p=permisos.findByCodigoCanonico(code).orElseThrow();
        if(grants.findByRol_IdRol(r.getIdRol()).stream().anyMatch(x->x.getPermiso().getIdPermiso().equals(p.getIdPermiso()))) return;
        RolPermiso row=new RolPermiso();
        row.setRol(r);
        row.setPermiso(p);
        row.setActivo(true);
        row.setPuedeDelegar(delegable);
        grants.save(row);
    }
}
