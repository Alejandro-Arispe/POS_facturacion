package com.pos.empresas.dto;

import com.pos.empresas.domain.TipoMovimientoCaja;

public record TipoMovimientoCajaResponse(Integer idTipoMovimientoCaja, String nombre, String descripcion) {

    public static TipoMovimientoCajaResponse from(TipoMovimientoCaja tipo) {
        return new TipoMovimientoCajaResponse(
                tipo.getIdTipoMovimientoCaja(),
                tipo.getNombre(),
                tipo.getDescripcion());
    }
}
