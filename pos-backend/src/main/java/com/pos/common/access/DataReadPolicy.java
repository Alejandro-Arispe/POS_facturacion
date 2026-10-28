package com.pos.common.access;

import java.util.*;
import org.springframework.stereotype.Component;
import com.pos.common.exception.ApiException;
import com.pos.empresas.repo.SucursalRepository;
import com.pos.usuarios.domain.Usuario;

/** Shared boundary for reports, files, snapshots and external data consumers. */
@Component
public class DataReadPolicy {
    private final EmpresaAccess access;
    private final RbacResolver resolver;
    private final SucursalRepository branches;
    public DataReadPolicy(EmpresaAccess access,RbacResolver resolver,SucursalRepository branches) {
        this.access=access; this.resolver=resolver; this.branches=branches;
    }
    public RbacResolver.Grants require(Usuario user,Integer company,Integer branch,String... codes) {
        if(branch==null) access.requireEmpresa(user,company); else access.requireSucursal(user,company,branch);
        var grants=branch==null?resolver.resolveCompany(user,company):resolver.resolve(user,company,branch);
        for(String code:codes) if(!grants.allows(code)) throw ApiException.forbidden("No tienes acceso a los datos requeridos en este ámbito: "+code);
        return grants;
    }
    public void productFields(RbacResolver.Grants grants,String... fields) {
        for(String field:fields) if(!resolver.field(grants,"product","read",field))
            throw ApiException.forbidden("La consulta utiliza un campo de producto restringido: "+field);
    }
    public List<Integer> branches(Usuario user,Integer company,String... codes) {
        access.requireEmpresa(user,company);
        return branches.findByEmpresa_IdEmpresa(company).stream().filter(b -> {
            var id=b.getIdSucursal();
            return Arrays.stream(codes).allMatch(code -> resolver.allowsInBranch(user,company,id,code));
        }).map(b -> b.getIdSucursal()).toList();
    }
    public record QueryScope(RbacResolver.Grants grants,List<Integer> branches,boolean available) {}
    public QueryScope query(Usuario user,Integer company,String source) {
        access.requireEmpresa(user,company);
        String permission=sourcePermission(source);
        if(Set.of("productos","clientes").contains(source)) {
            var grants=resolver.resolve(user,company,null);
            return new QueryScope(grants,List.of(),grants.allows("report:generate") && grants.allows(permission));
        }
        var request=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        boolean exporting=request instanceof org.springframework.web.context.request.ServletRequestAttributes attrs && "true".equals(attrs.getRequest().getHeader("X-Report-Export"));
        List<Integer> allowed=exporting?branches(user,company,"report:generate",permission,"report:export"):branches(user,company,"report:generate",permission);
        Map<String,com.pos.usuarios.domain.Permiso> common=new LinkedHashMap<>();
        for(Integer branch:allowed) {
            var grants=resolver.resolve(user,company,branch);
            if(common.isEmpty() && branch.equals(allowed.get(0))) common.putAll(grants.permissions());
            else common.keySet().retainAll(grants.permissions().keySet());
        }
        return new QueryScope(new RbacResolver.Grants(Collections.unmodifiableMap(common),Set.of(),List.of()),allowed,!allowed.isEmpty());
    }
    public String sourcePermission(String source) {
        return switch(source) {
            case "ventas","productos_vendidos" -> "sale:read";
            case "inventario" -> "inventory:read";
            case "movimientos" -> "inventory_movement:read";
            case "clientes" -> "client:read";
            case "productos" -> "product:read";
            default -> throw ApiException.badRequest("Fuente de datos no admitida.");
        };
    }
    public String productField(String source,String column) {
        if(source.equals("clientes") || source.equals("ventas")) return null;
        return switch(column) {
            case "producto","nombre_producto","nombre" -> "nombre";
            case "categoria","subcategoria","id_categoria" -> "id_subcategoria";
            case "unidad","unidad_medida" -> "unidad_medida";
            case "codigo_barra","codigo_barras" -> "codigo_barra";
            case "precio","valor" -> "precio";
            case "costo","costo_total","ganancia","margen" -> "costo";
            case "imagen" -> "imagen";
            case "activo" -> "activo";
            default -> null;
        };
    }
    public boolean column(RbacResolver.Grants grants,String source,String column) {
        String field=productField(source,column);
        if(field!=null && !resolver.field(grants,"product","read",field)) return false;
        return !Set.of("usuario","vendedor").contains(column) || grants.allows("user:read");
    }
    public void columnRequired(RbacResolver.Grants grants,String source,String column) {
        if(!column(grants,source,column)) throw ApiException.forbidden("No puedes seleccionar, filtrar, ordenar ni agregar ese campo.");
    }
}
