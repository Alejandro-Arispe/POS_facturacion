package com.pos.reportes.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class ReporteDtos {

    private ReporteDtos() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Plantilla {
        private String identificador;
        private String nombre;
        private String descripcion;
        private List<String> metricas = new ArrayList<>();
        private List<String> dimensiones = new ArrayList<>();
        private String formato = "tabla";
        private Map<String, Object> filtrosPorDefecto = new LinkedHashMap<>();
        private List<String> etiquetas = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Solicitud {
        private String prompt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Especificacion {
        private String identificadorPlantilla;
        private String titulo;
        private List<String> metricas = new ArrayList<>();
        private List<String> dimensiones = new ArrayList<>();
        private Map<String, Object> filtros = new LinkedHashMap<>();
        private String formato = "tabla";
        private Boolean solicitaAclaracion = Boolean.FALSE;
        private String pregunta;
        private Double confianza;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Columna {
        private String nombre;
        private String etiqueta;
        private String tipo = "texto";
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RespuestaInterpretacion {
        private Especificacion especificacion;
        private Plantilla plantilla;
        private List<String> advertencias = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RespuestaReporte {
        private String idReporte;
        private String titulo;
        private String identificadorPlantilla;
        private String estado = "listo";
        private Especificacion especificacion;
        private List<Columna> columnas = new ArrayList<>();
        private List<Map<String, Object>> filas = new ArrayList<>();
        private Map<String, Object> agregados = new LinkedHashMap<>();
        private Map<String, Object> grafico;
        private List<String> advertencias = new ArrayList<>();
        private LocalDate fechaGeneracion;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SucursalRequest {
        private Integer idSucursal;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ProductoAbastecimiento {
        private Integer idProducto;
        private String producto;
        private Integer vendidoUltimos30Dias;
        private Integer stockActual;
        private Double promedioDiario;
        private Integer prediccionProximos30Dias;
        private Integer recomendadoComprar;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecomendacionRequest {
        private List<Integer> idProductos = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ProductoRecomendado {
        private Integer idProducto;
        private String nombre;
        private String unidadMedida;
        private Double precio;
        private Integer stock;
        private String codigo;
        private String codigoBarras;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RecomendacionResponse {
        private List<Integer> productosAnalizados = new ArrayList<>();
        private List<ProductoRecomendado> recomendaciones = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VentasParamRequest {
        private LocalDate fechaInicial;
        private LocalDate fechaFinal;
        private Integer idSucursal;
        private Integer idTipoVenta;
        private Integer idMetodoPago;
        private Integer idProducto;
        private Integer idUsuario;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InventarioParamRequest {
        private LocalDate fechaInicial;
        private LocalDate fechaFinal;
        private Integer idSucursal;
        private Integer idTipoMovimiento;
        private Integer idProducto;
        private Integer idCategoriaProducto;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CajasParamRequest {
        private LocalDate fechaInicial;
        private LocalDate fechaFinal;
        private Integer idSucursal;
        private Integer idCaja;
        private Integer idTipoMovimientoCaja;
        private String estadoSesion;
    }
}
