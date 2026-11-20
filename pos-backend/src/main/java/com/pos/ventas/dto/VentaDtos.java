package com.pos.ventas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class VentaDtos {

    private VentaDtos() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CatalogoItem {
        private Integer idTipoVenta;
        private Integer idMetodoPago;
        private String nombre;
        private String descripcion;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DetalleCreate {
        private Integer idProducto;
        private Integer cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal descuento = BigDecimal.ZERO;
        private BigDecimal subtotal;
        private String descripcion;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PagoCreate {
        private Integer idMetodoPago;
        private BigDecimal monto;
        /** Id del cobro QR en la Billetera (solo metodo QR, cuando la pasarela esta configurada). */
        private String referenciaBilletera;
        private ChequeCreate cheque;
    }

    public record ChequeCreate(String banco, String numero, LocalDate fechaEmision, Boolean cobrado) {}

    public record ChequeDetalle(String banco, String numero, LocalDate fechaEmision) {}

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnulacionCreate {
        private String motivo;
        /** Motivo SIN para anular la factura (1 a 4); por defecto 1. */
        private Integer codigoMotivoSin;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DevolucionItem {
        private Integer idProducto;
        private Integer cantidad;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DevolucionCreate {
        private List<DevolucionItem> items = new ArrayList<>();
        /** EFECTIVO o SALDO_A_FAVOR. */
        private String tipoReembolso;
        private String motivo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VentaCreate {
        private Integer idTipoVenta;
        private Integer idCliente;
        private Integer idMetodoPago;
        private Integer idPedido;
        private Boolean facturaLinea = Boolean.FALSE;
        /** Datos del comprador para la factura SIAT simulada (opcionales; se toman del cliente si faltan). */
        private Integer codigoTipoDocumento;
        private String numeroDocumento;
        private String razonSocial;
        private List<PagoCreate> pagos;
        private BigDecimal subtotal;
        private BigDecimal descuentoTotal;
        private BigDecimal total;
        private String estado;
        private List<DetalleCreate> detalles = new ArrayList<>();
        /** HU-09: credenciales del administrador que autoriza exceder el límite de crédito. */
        private AutorizacionCreditoCreate autorizacionCredito;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AutorizacionCreditoCreate {
        private String email;
        private String contrasena;
        private String motivo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DetalleResponse {
        private Integer idDetalleVenta;
        private Integer idProducto;
        private Integer cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal descuento;
        private BigDecimal subtotal;
        private BigDecimal total;
        private String descripcion;
        private ProductoSimple producto;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public static class ProductoSimple {
        private Integer idProducto;
        private String nombre;
        private String codigoBarra;
        private String unidadMedida;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PagoResponse {
        private Integer idMetodoPago;
        private BigDecimal monto;
        private LocalDateTime fecha;
        private CatalogoItem metodoPago;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private ChequeDetalle cheque;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class VentaResponse {
        private Integer idVenta;
        private Integer idUsuario;
        private Integer idCajaSesion;
        private Integer idCliente;
        private Integer idTipoVenta;
        private BigDecimal subtotal;
        private BigDecimal descuentoTotal;
        private BigDecimal total;
        private LocalDateTime fecha;
        private String estado;
        private Integer idMetodoPago;
        private CatalogoItem metodoPago;
        private String tipoVentaNombre;
        private Integer idFactura;
        private String cuf;
        private Integer numeroFactura;
        private String motivoAnulacion;
        private String estadoSiat;
        private List<DetalleResponse> detalles = new ArrayList<>();
        private List<PagoResponse> pagos = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PagoCreditoItem {
        private Integer idMetodoPago;
        private BigDecimal montoPagado;
        private ChequeCreate cheque;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CobroCreate {
        private Integer idCxc;
        private List<PagoCreditoItem> pagosCredito = new ArrayList<>();
        /** Opcional: pago informado por el cliente que este cobro confirma. */
        private Integer idPagoReportado;
    }

    /** HU-10: pago de crédito que el cliente informa desde la app (queda PENDIENTE). */
    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PagoReportadoCreate {
        private Integer idMetodoPago;
        private BigDecimal montoPagado;
        private String referencia;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PagoReportadoResponse {
        private Integer idPagoReportado;
        private Integer idCxc;
        private Integer idMetodoPago;
        private BigDecimal monto;
        private String referencia;
        private String estado;
        private LocalDateTime fechaReporte;
        private LocalDateTime fechaConfirmacion;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PagoCreditoResponse {
        private Integer idPagoCredito;
        private Integer idMetodoPago;
        private BigDecimal montoPagado;
        private LocalDateTime fechaPago;
        private CatalogoItem metodoPago;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private ChequeDetalle cheque;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class MovimientoCajaResponse {
        private Integer idMovimientoCaja;
        private Integer idMetodoPago;
        private Integer idTipoMovimientoCaja;
        private Integer idCajaSesion;
        private Integer idUsuario;
        private LocalDateTime fecha;
        private BigDecimal monto;
        private String concepto;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CobroResponse {
        private Integer idCxc;
        private Integer idCajaSesion;
        private BigDecimal montoCredito;
        private BigDecimal saldoAnterior;
        private BigDecimal totalPagado;
        private BigDecimal saldoPendiente;
        private String estado;
        private List<PagoCreditoResponse> pagosCredito = new ArrayList<>();
        private List<MovimientoCajaResponse> movimientosCaja = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class HistorialPagoResponse {
        private Integer idCxc;
        private Integer idVenta;
        private Integer idPagoCredito;
        private BigDecimal montoPagado;
        private LocalDateTime fechaPago;
        private CatalogoItem metodoPago;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private ChequeDetalle cheque;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CuentaPorCobrarResponse {
        private Integer idCxc;
        private Integer idVenta;
        private BigDecimal montoCredito;
        private BigDecimal saldoPendiente;
        private LocalDateTime fechaInicio;
        private LocalDateTime fechaVencimiento;
        private String estado;
        private VentaResponse venta;
        private List<PagoCreditoResponse> pagosCredito = new ArrayList<>();
        private List<PagoReportadoResponse> pagosReportados = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class FacturaResponse {
        private Integer idFactura;
        private Integer idVenta;
        private String nitEmisor;
        private Integer numeroFactura;
        private LocalDateTime fechaEmision;
        private String nitCliente;
        private String nombreCliente;
        private BigDecimal montoTotal;
        private BigDecimal iva;
        private String cufd;
        private String cuf;
        private String xmlGenerado;
        private String pdfGenerado;
    }
}
