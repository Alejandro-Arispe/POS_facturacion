package com.pos.billetera.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cobro QR creado en la Billetera (pasarela de pagos). Se guarda para conocer su estado y para que un
 * cobro pagado se use en una sola venta.
 */
@Entity
@Table(name = "cobro_billetera")
@Getter
@Setter
@NoArgsConstructor
public class CobroBilletera {

    /** Id del cobro en la billetera (UUID). */
    @Id
    @Column(name = "id_cobro", length = 36)
    private String idCobro;

    @Column(name = "referencia", nullable = false, unique = true, length = 64)
    private String referencia;

    @Column(name = "id_empresa", nullable = false)
    private Integer idEmpresa;

    @Column(name = "id_sucursal")
    private Integer idSucursal;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "moneda", nullable = false, length = 3)
    private String moneda = "BOB";

    /** CREATED, SCANNED, PROCESSING, PAID, EXPIRED o CANCELLED (el de la billetera). */
    @Column(name = "estado", nullable = false, length = 12)
    private String estado;

    @Column(name = "pagado_por", length = 120)
    private String pagadoPor;

    /** Venta en la que se uso el cobro; null mientras no se haya registrado la venta. */
    @Column(name = "id_venta")
    private Integer idVenta;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "vence", nullable = false)
    private LocalDateTime vence;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;
}
