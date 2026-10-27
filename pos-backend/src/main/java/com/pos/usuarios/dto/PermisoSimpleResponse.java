package com.pos.usuarios.dto;
import com.pos.usuarios.domain.Permiso;
public record PermisoSimpleResponse(Integer idPermiso,String codigo,String nombre,String codigoCanonico,String recurso,String accion,String atributo,String descripcion,Boolean activo) {
    public PermisoSimpleResponse(Integer id,String codigo,String nombre){this(id,codigo,nombre,null,null,null,null,null,true);}
    public static PermisoSimpleResponse from(Permiso p){return new PermisoSimpleResponse(p.getIdPermiso(),p.getCodigo(),p.getNombre(),p.getCodigoCanonico(),p.getRecurso(),p.getAccion(),p.getAtributo(),p.getDescripcion(),p.getActivo());}
}
