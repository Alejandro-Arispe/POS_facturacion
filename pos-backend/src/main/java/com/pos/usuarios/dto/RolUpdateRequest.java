package com.pos.usuarios.dto;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.pos.common.exception.ApiException;
public record RolUpdateRequest(String nombre, String descripcion, Boolean activo, List<Integer> permisoIds, List<Integer> delegableIds, List<CampoEstadoRequest> campos) {
public RolUpdateRequest(Boolean activo,List<Integer> permisoIds) {this(null,null,activo,permisoIds,null,null);}
    @JsonAnySetter public void unknown(String key,Object value) {throw ApiException.badRequest("Atributo de rol no admitido: "+key);}
}
