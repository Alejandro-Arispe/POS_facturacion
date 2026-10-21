package com.pos.productos.domain;

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
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Integer idProducto;

    @Column(name = "id_empresa")
    private Integer idEmpresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_subcategoria")
    private SubcategoriaProducto subcategoria;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "codigo_barra", length = 100)
    private String codigoBarra;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "unidad_medida", nullable = false, length = 50)
    private String unidadMedida;

    @Column(name = "precio", nullable = false, precision = 12, scale = 2)
    private BigDecimal precio = BigDecimal.ZERO;

    @Column(name = "imagen", length = 255)
    private String imagen;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
