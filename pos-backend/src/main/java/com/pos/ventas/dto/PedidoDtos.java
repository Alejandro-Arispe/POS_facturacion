package com.pos.ventas.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class PedidoDtos {

    private PedidoDtos() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DetalleCreate {
        private Integer idProducto;
        private Integer cantidad;
        private BigDecimal precioUnitarioEstimado = BigDecimal.ZERO;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PedidoCreate {
        private Integer idSucursal;
        private String observacionCliente;
        private List<DetalleCreate> detalles = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EstadoUpdate {
        private String estado;
        private String observacionEmpresa;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DetalleResponse {
        private Integer idDetalle;
        private Integer idPedido;
        private Integer idProducto;
        private Integer cantidad;
        private BigDecimal precioUnitarioEstimado;
        private BigDecimal descuentoEstimado;
        private BigDecimal subtotalEstimado;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PedidoResponse {
        private Integer idPedido;
        private Integer idEmpresa;
        private Integer idSucursal;
        private Integer idCliente;
        private Integer idVenta;
        private String estado;
        private BigDecimal subtotalEstimado;
        private BigDecimal descuentoEstimado;
        private BigDecimal totalEstimado;
        private String observacionCliente;
        private String observacionEmpresa;
        private LocalDateTime fechaCreacion;
        private LocalDateTime fechaConfirmacion;
        private LocalDateTime fechaPreparacion;
        private LocalDateTime fechaListo;
        private LocalDateTime fechaCancelacion;
        private List<DetalleResponse> detalles = new ArrayList<>();
    }
}
