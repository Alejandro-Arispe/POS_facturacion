package com.pos.usuarios.dto;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.pos.common.exception.ApiException;
public record RolCreateRequest(String nombre, String descripcion, Boolean activo, List<Integer> permisoIds, List<Integer> delegableIds, List<CampoEstadoRequest> campos) {
public RolCreateRequest(String nombre,List<Integer> permisoIds) {this(nombre,null,true,permisoIds,List.of(),null);}
    @JsonAnySetter public void unknown(String key,Object value) {throw ApiException.badRequest("Atributo de rol no admitido: "+key);}
}
