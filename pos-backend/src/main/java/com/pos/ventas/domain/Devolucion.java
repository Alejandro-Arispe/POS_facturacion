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

/**
 * Devolucion parcial o total de una venta (HU-15). Si la venta tenia factura se registra una
 * Nota de Credito-Debito simulada, como exige el SIN para devoluciones de productos.
 */
@Entity
@Table(name = "devolucion")
@Getter
@Setter
@NoArgsConstructor
public class Devolucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_devolucion")
    private Integer idDevolucion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_venta", nullable = false)
    private Venta venta;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    /** Sesion de caja desde la que sale el efectivo (null si el reembolso es saldo a favor). */
    @Column(name = "id_caja_sesion")
    private Integer idCajaSesion;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "motivo", nullable = false, length = 255)
    private String motivo;

    /** EFECTIVO o SALDO_A_FAVOR. */
    @Column(name = "tipo_reembolso", nullable = false, length = 20)
    private String tipoReembolso;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto = BigDecimal.ZERO;

    /** Codigo de la Nota de Credito-Debito simulada (null si la venta no tenia factura). */
    @Column(name = "codigo_nota_credito", length = 80)
    private String codigoNotaCredito;
}
