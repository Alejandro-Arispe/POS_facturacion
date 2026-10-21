package com.pos.clientes.domain;

import java.math.BigDecimal;

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
@Table(name = "categoria_cliente")
@Getter
@Setter
@NoArgsConstructor
public class CategoriaCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria_cliente")
    private Integer idCategoriaCliente;

    @Column(name = "id_empresa", nullable = false)
    private Integer idEmpresa;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "plazo_credito", nullable = false)
    private Integer plazoCredito = 0;

    @Column(name = "descuento_base", nullable = false, precision = 5, scale = 2)
    private BigDecimal descuentoBase = BigDecimal.ZERO;

    @Column(name = "limite_credito", nullable = false, precision = 12, scale = 2)
    private BigDecimal limiteCredito = BigDecimal.ZERO;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
