package com.pos.common.access;

import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.pos.common.exception.ApiException;

/** Shared field checks. A write capability never implies a read capability. */
@Component
public class FieldAuthorization {
    public boolean allows(RbacResolver.Grants grants, String resource, String action, String field) {
        return grants.allows(resource + ":" + action) && grants.allows(resource + ":" + action + ":" + field);
    }
    public void require(RbacResolver.Grants grants, String resource, String action, Collection<String> fields) {
        if (!grants.allows(resource + ":" + action)) throw ApiException.forbidden("No tienes permiso para esta operación.");
        for (String field : fields) if (!allows(grants,resource,action,field))
            throw ApiException.forbidden("No puedes " + (action.equals("create")?"establecer":"modificar") + " el campo: " + field);
    }
    public void project(Map<String,Object> target, RbacResolver.Grants grants, String resource, String field, Object value) {
        if (allows(grants,resource,"read",field)) target.put(field,value);
    }
}
