package com.pos.usuarios.dto;

import java.util.List;

public record RolDetalleResponse(
        Integer idRol,
        String nombre,
        Integer idEmpresa,
        String tipo,
        String descripcion,
        Boolean activo,
        List<RolPermisoResponse> rolPermisos) {
}
