package com.pos.usuarios.dto;

import java.util.List;

public record RolDetalleResponse(
        Integer idRol,
        String nombre,
        Integer idEmpresa,
        String tipo,
        String descripcion,
        Boolean activo,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) List<RolPermisoResponse> rolPermisos) {
}
