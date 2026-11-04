package com.pos.siat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Catálogo paramétrico SIN: producto/servicio homologado por actividad económica (simulado). */
@Entity
@Table(name = "sin_productos_servicios")
@Getter
@Setter
@NoArgsConstructor
public class SinProductoServicio {

    @Id
    @Column(name = "codigo_producto_sin")
    private Integer codigoProductoSin;

    @Column(name = "codigo_actividad", nullable = false, length = 10)
    private String codigoActividad;

    @Column(name = "descripcion_producto_sin", nullable = false, columnDefinition = "TEXT")
    private String descripcionProductoSin;
}
