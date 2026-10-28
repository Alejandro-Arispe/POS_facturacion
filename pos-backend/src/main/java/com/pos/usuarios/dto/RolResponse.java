package com.pos.usuarios.dto;

import com.pos.usuarios.domain.Rol;

public record RolResponse(
        Integer idRol,
        String nombre,
        Integer idEmpresa,
        String tipo,
        String descripcion,
        Boolean activo, Boolean protegido, Boolean administrable) {

    public static RolResponse from(Rol rol) { return from(rol,false); }

    public static RolResponse from(Rol rol, boolean administrable) {
        return new RolResponse(
                rol.getIdRol(),
                rol.getNombre(),
                rol.getIdEmpresa(),
                rol.getTipo(),
                rol.getDescripcion(),
                rol.getActivo(), rol.getProtegido(), administrable);
    }
}
