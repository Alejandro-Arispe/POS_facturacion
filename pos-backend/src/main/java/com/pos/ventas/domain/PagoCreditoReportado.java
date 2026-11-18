package com.pos.ventas.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * HU-10: pago de crédito informado por el cliente desde la app. Queda PENDIENTE y no toca el saldo
 * hasta que el personal lo confirma registrando el cobro en caja (pagos-credito con id_pago_reportado).
 */
@Entity
@Table(name = "pago_credito_reportado")
@Getter
@Setter
@NoArgsConstructor
public class PagoCreditoReportado {
    public static final String PENDIENTE = "PENDIENTE";
    public static final String CONFIRMADO = "CONFIRMADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago_reportado")
    private Integer idPagoReportado;

    @Column(name = "id_cxc", nullable = false)
    private Integer idCxc;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "id_metodo_pago", nullable = false)
    private Integer idMetodoPago;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "referencia", length = 100)
    private String referencia;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = PENDIENTE;

    @Column(name = "fecha_reporte", nullable = false)
    private LocalDateTime fechaReporte;

    @Column(name = "id_usuario_confirma")
    private Integer idUsuarioConfirma;

    @Column(name = "fecha_confirmacion")
    private LocalDateTime fechaConfirmacion;
}
