package com.pos.usuarios.dto;

import com.pos.usuarios.domain.Rol;

public record RolResponse(
        Integer idRol,
        String nombre,
        Integer idEmpresa,
        String tipo,
        String descripcion,
        Boolean activo) {

    public static RolResponse from(Rol rol) {
        return new RolResponse(
                rol.getIdRol(),
                rol.getNombre(),
                rol.getIdEmpresa(),
                rol.getTipo(),
                rol.getDescripcion(),
                rol.getActivo());
    }
}
