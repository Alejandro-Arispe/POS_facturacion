package com.pos.usuarios.dto;

import com.pos.usuarios.domain.RolPermiso;

public record RolPermisoResponse(
        Integer idRolPermiso,
        Integer idRol,
        Integer idPermiso,
        Boolean activo) {

    public static RolPermisoResponse from(RolPermiso rolPermiso) {
        return new RolPermisoResponse(
                rolPermiso.getIdRolPermiso(),
                rolPermiso.getRol() != null ? rolPermiso.getRol().getIdRol() : null,
                rolPermiso.getPermiso() != null ? rolPermiso.getPermiso().getIdPermiso() : null,
                rolPermiso.getActivo());
    }
}
