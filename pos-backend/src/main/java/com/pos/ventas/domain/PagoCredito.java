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
@Table(name = "pago_credito")
@Getter
@Setter
@NoArgsConstructor
public class PagoCredito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago_credito")
    private Integer idPagoCredito;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cxc", nullable = false)
    private CuentaPorCobrar cuentaPorCobrar;

    @Column(name = "id_metodo_pago", nullable = false)
    private Integer idMetodoPago;

    @Column(name = "monto_pagado", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoPagado = BigDecimal.ZERO;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDateTime fechaPago;
}
