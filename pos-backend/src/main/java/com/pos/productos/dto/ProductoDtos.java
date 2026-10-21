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

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductoCreate {
        private Integer idSubcategoria;
        private String nombre;
        private String codigoBarra;
        private String descripcion;
        private String unidadMedida;
        private BigDecimal precio = BigDecimal.ZERO;
        private Boolean activo = Boolean.TRUE;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductoUpdate {
        private Integer idSubcategoria;
        private String nombre;
        private String codigoBarra;
        private String descripcion;
        private String unidadMedida;
        private BigDecimal precio;
        private Boolean activo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ProductoResponse {
        private Integer idProducto;
        private Integer idEmpresa;
        private Integer idSubcategoria;
        private String nombre;
        private String codigoBarra;
        private String descripcion;
        private String unidadMedida;
        private BigDecimal precio;
        private String imagen;
        private Boolean activo;
        private SubcategoriaResponse subcategoria;
    }
}
