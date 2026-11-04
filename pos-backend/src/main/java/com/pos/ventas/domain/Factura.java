package com.pos.ventas.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "factura")
@Getter
@Setter
@NoArgsConstructor
public class Factura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_factura")
    private Integer idFactura;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_venta", nullable = false, unique = true)
    private Venta venta;

    @Column(name = "nit_emisor", nullable = false, length = 30)
    private String nitEmisor;

    @Column(name = "numero_factura", nullable = false)
    private Integer numeroFactura;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;

    @Column(name = "nit_cliente", nullable = false, length = 30)
    private String nitCliente;

    @Column(name = "monto_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoTotal = BigDecimal.ZERO;

    @Column(name = "iva", nullable = false, precision = 12, scale = 2)
    private BigDecimal iva = BigDecimal.ZERO;

    @Column(name = "cufd", nullable = false, length = 100)
    private String cufd;

    @Column(name = "cuf", nullable = false, unique = true, length = 200)
    private String cuf;

    @Column(name = "xml_generado", nullable = false, columnDefinition = "TEXT")
    private String xmlGenerado;

    @Column(name = "pdf_generado", nullable = false, columnDefinition = "TEXT")
    private String pdfGenerado;

    // Campos de la simulación SIAT. Son nullables para no romper filas anteriores.
    @Column(name = "cuis", length = 20)
    private String cuis;

    /** EMITIDA, ANULADA o CONTINGENCIA. */
    @Column(name = "estado_siat", length = 20)
    private String estadoSiat;

    @Column(name = "codigo_motivo_anulacion")
    private Integer codigoMotivoAnulacion;

    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    @Column(name = "qr_code_data", columnDefinition = "TEXT")
    private String qrCodeData;

    @Column(name = "razon_social_cliente", length = 200)
    private String razonSocialCliente;

    /** Código SIN del tipo de documento de identidad del cliente (1 CI, 2 CEX, 3 PAS, 4 OD, 5 NIT). */
    @Column(name = "codigo_tipo_documento")
    private Integer codigoTipoDocumento;

    /** Código SIN del método de pago (1 efectivo, 2 tarjeta, 7 transferencia/QR). */
    @Column(name = "codigo_metodo_pago")
    private Integer codigoMetodoPago;
}
