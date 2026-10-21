package com.pos.clientes.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class ClienteDtos {

    private ClienteDtos() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CategoriaCreate {
        private String nombre;
        private String descripcion;
        private Integer plazoCredito = 0;
        private BigDecimal descuentoBase = BigDecimal.ZERO;
        private BigDecimal limiteCredito = BigDecimal.ZERO;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CategoriaUpdate {
        private Integer idCategoriaCliente;
        private String nombre;
        private String descripcion;
        private Integer plazoCredito;
        private BigDecimal descuentoBase;
        private BigDecimal limiteCredito;
        private Boolean activo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CategoriaResponse {
        private Integer idCategoriaCliente;
        private Integer idEmpresa;
        private String nombre;
        private String descripcion;
        private Integer plazoCredito;
        private BigDecimal descuentoBase;
        private BigDecimal limiteCredito;
        private Boolean activo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClienteCreate {
        private Integer idCategoriaCliente;
        private String codigoCliente;
        private BigDecimal saldoCredito = BigDecimal.ZERO;
        private BigDecimal limiteCredito = BigDecimal.ZERO;
        private Boolean activo = Boolean.TRUE;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClienteUpdate {
        private Integer idCategoriaCliente;
        private String codigoCliente;
        private BigDecimal saldoCredito;
        private BigDecimal limiteCredito;
        private Boolean activo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ClienteResponse {
        private Integer idCliente;
        private Integer idUsuario;
        private Integer idCategoriaCliente;
        private String codigoCliente;
        private BigDecimal saldoCredito;
        private BigDecimal limiteCredito;
        private Boolean activo;
    }
}
