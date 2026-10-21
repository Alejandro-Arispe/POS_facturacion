package com.pos.usuarios.dto;

import java.util.List;

public record PermisosPorModuloResponse(
        Integer idModulo,
        String codigo,
        String nombre,
        List<PermisoSimpleResponse> permisos) {
}
