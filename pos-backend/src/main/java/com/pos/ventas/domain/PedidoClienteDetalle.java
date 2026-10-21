package com.pos.ventas.domain;

import java.math.BigDecimal;

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
@Table(name = "pedido_cliente_detalle")
@Getter
@Setter
@NoArgsConstructor
public class PedidoClienteDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Integer idDetalle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_pedido", nullable = false)
    private PedidoCliente pedido;

    @Column(name = "id_producto", nullable = false)
    private Integer idProducto;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "precio_unitario_estimado", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitarioEstimado = BigDecimal.ZERO;

    @Column(name = "descuento_estimado", nullable = false, precision = 12, scale = 2)
    private BigDecimal descuentoEstimado = BigDecimal.ZERO;

    @Column(name = "subtotal_estimado", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalEstimado = BigDecimal.ZERO;
}
