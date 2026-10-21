package com.pos.inventario.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class InventarioDtos {

    private InventarioDtos() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class TipoMovimientoResponse {
        private Integer idTipoMovimiento;
        private String nombre;
        private String descripcion;
        private String direccion;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MovimientoCreate {
        private Integer idProducto;
        private Integer idTipoMovimiento;
        private Integer cantidad;
        private String observacion;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class MovimientoResponse {
        private Integer idMovimientoInventario;
        private Integer idProducto;
        private Integer idTipoMovimiento;
        private Integer idUsuario;
        private Integer idSucursal;
        private Integer cantidad;
        private String observacion;
        private LocalDateTime fechaMovimiento;
        private Integer stockActual;
        private TipoMovimientoResponse tipoMovimiento;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class StockResponse {
        private Integer idStock;
        private Integer idProducto;
        private Integer idSucursal;
        private Integer cantidad;
        private Integer stockMinimo;
        private Integer stockMaximo;
        private LocalDateTime fechaActualizacion;
        private String nombreProducto;
        private String codigoBarra;
        private String unidadMedida;
        private double precio;
        private String imagen;
        private Boolean activo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StockUpdate {
        private Integer stockMinimo;
        private Integer stockMaximo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ProductoSimple {
        private Integer idProducto;
        private String nombre;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class TipoSimple {
        private Integer idTipoMovimiento;
        private String nombre;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class MovimientoListItem {
        private Integer idMovimiento;
        private Integer idProducto;
        private Integer cantidad;
        private String observacion;
        private String tipo;
        private ProductoSimple producto;
        private TipoSimple tipoMovimiento;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Unused {
        private BigDecimal ignored;
    }
}
