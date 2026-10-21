package com.pos.ventas.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "venta_pago")
@IdClass(VentaPagoId.class)
@Getter
@Setter
@NoArgsConstructor
public class VentaPago {

    @Id
    private Integer idVenta;

    @Id
    @Column(name = "id_metodo_pago", nullable = false)
    private Integer idMetodoPago;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("idVenta")
    @JoinColumn(name = "id_venta", nullable = false)
    private Venta venta;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto = BigDecimal.ZERO;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;
}
