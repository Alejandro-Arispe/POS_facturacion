package com.pos.productos.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class ProductoDtos {

    private ProductoDtos() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CategoriaCreate {
        private String nombre;
        private String descripcion;
        private Boolean activo = Boolean.TRUE;
        private Integer idEmpresa;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CategoriaUpdate {
        private String nombre;
        private String descripcion;
        private Boolean activo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CategoriaResponse {
        private Integer idCategoriaProducto;
        private Integer idEmpresa;
        private String nombre;
        private String descripcion;
        private Boolean activo;
        private List<SubcategoriaResponse> subcategorias = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubcategoriaCreate {
        private Integer idCategoriaProducto;
        private String nombre;
        private String descripcion;
        private Boolean activo = Boolean.TRUE;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubcategoriaUpdate {
        private Integer idCategoriaProducto;
        private String nombre;
        private String descripcion;
        private Boolean activo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SubcategoriaResponse {
        private Integer idSubcategoria;
        private Integer idCategoriaProducto;
        private String nombre;
        private String descripcion;
        private Boolean activo;
    }

    /** Setters track presence: omitted and explicit null are different operations. */
    @Getter
    @NoArgsConstructor
    public static class ProductoUpdate {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private final java.util.Set<String> provided = new java.util.LinkedHashSet<>();
        private Integer idSubcategoria;
        private String nombre;
        private String codigoBarra;
        private String descripcion;
        private String unidadMedida;
        private BigDecimal precio;
        private BigDecimal costo;
        private Boolean activo;
        private String imagen;
        public void setIdSubcategoria(Integer v) { provided.add("id_subcategoria"); idSubcategoria=v; }
        public void setNombre(String v) { provided.add("nombre"); nombre=v; }
        public void setCodigoBarra(String v) { provided.add("codigo_barra"); codigoBarra=v; }
        public void setDescripcion(String v) { provided.add("descripcion"); descripcion=v; }
        public void setUnidadMedida(String v) { provided.add("unidad_medida"); unidadMedida=v; }
        public void setPrecio(BigDecimal v) { provided.add("precio"); precio=v; }
        public void setCosto(BigDecimal v) { provided.add("costo"); costo=v; }
        public void setActivo(Boolean v) { provided.add("activo"); activo=v; }
        public void setImagen(String v) { provided.add("imagen"); imagen=v; }
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void unknown(String field, Object value) {
            throw com.pos.common.exception.ApiException.badRequest("Atributo de producto no admitido: " + field);
        }
    }

    public static class ProductoCreate extends ProductoUpdate { }
}
