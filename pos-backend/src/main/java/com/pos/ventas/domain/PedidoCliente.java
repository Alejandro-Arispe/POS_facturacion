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

@Entity
@Table(name = "pedido_cliente")
@Getter
@Setter
@NoArgsConstructor
public class PedidoCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Integer idPedido;

    @Column(name = "id_empresa", nullable = false)
    private Integer idEmpresa;

    @Column(name = "id_sucursal", nullable = false)
    private Integer idSucursal;

    @Column(name = "id_cliente", nullable = false)
    private Integer idCliente;

    @Column(name = "id_venta")
    private Integer idVenta;

    @Column(name = "estado", nullable = false, length = 50)
    private String estado = "enviado";

    @Column(name = "subtotal_estimado", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalEstimado = BigDecimal.ZERO;

    @Column(name = "descuento_estimado", nullable = false, precision = 12, scale = 2)
    private BigDecimal descuentoEstimado = BigDecimal.ZERO;

    @Column(name = "total_estimado", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalEstimado = BigDecimal.ZERO;

    @Column(name = "observacion_cliente", columnDefinition = "TEXT")
    private String observacionCliente;

    @Column(name = "observacion_empresa", columnDefinition = "TEXT")
    private String observacionEmpresa;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_confirmacion")
    private LocalDateTime fechaConfirmacion;

    @Column(name = "fecha_preparacion")
    private LocalDateTime fechaPreparacion;

    @Column(name = "fecha_listo")
    private LocalDateTime fechaListo;

    @Column(name = "fecha_cancelacion")
    private LocalDateTime fechaCancelacion;
}
