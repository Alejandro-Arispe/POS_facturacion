package com.pos.notifications.service;

import java.util.*;
import org.springframework.stereotype.Component;
import com.pos.common.access.*;
import com.pos.productos.repo.ProductoRepository;
import com.pos.usuarios.domain.Usuario;

@Component
public class NotificationReadPolicy {
    private final EmpresaAccess access; private final RbacResolver rbac; private final ProductoRepository products;
    public NotificationReadPolicy(EmpresaAccess access,RbacResolver rbac,ProductoRepository products){this.access=access;this.rbac=rbac;this.products=products;}
    public boolean visible(Usuario user,Integer company,Map<String,Object> payload) {
        if(user==null || company==null) return false;
        Integer branch=number(payload.get("id_sucursal"));
        if(!access.canAccess(user,company,branch,"")) return false;
        Object recipient=payload.get("id_usuario");
        if(recipient!=null) return String.valueOf(user.getIdUsuario()).equals(String.valueOf(recipient));
        if(payload.containsKey("id_producto")) {
            var product=products.findById(Optional.ofNullable(number(payload.get("id_producto"))).orElse(-1)).orElse(null);
            return product!=null && company.equals(product.getIdEmpresa()) && branch!=null
                && access.canPermiso(user,company,branch,"inventory_alert:read") && access.canPermiso(user,company,branch,"inventory:read")
                && rbac.field(rbac.resolve(user,company,branch),"product","read","nombre");
        }
        if(!access.canCompanyPermission(user,company,"company:update")) return false;
        var grants=rbac.resolveCompany(user,company);
        return ProductFields.ALL.stream().allMatch(f -> rbac.field(grants,"product","read",f));
    }
    public Map<String,Object> payload(Map<String,Object> raw) {
        Map<String,Object> safe=new LinkedHashMap<>();
        for(String key:List.of("id_empresa","id_usuario","id_producto","id_sucursal","id_cxc","nombre_producto","stock_actual","stock_minimo","monto_credito","monto_pagado","saldo_pendiente","estado")) {
            Object value=raw.get(key);
            if(value instanceof Number || value instanceof Boolean || value instanceof String text && text.length()<=200) safe.put(key,value);
        }
        return safe;
    }
    private Integer number(Object value){try{return value==null?null:Integer.valueOf(value.toString());}catch(Exception ex){return null;}}
}
