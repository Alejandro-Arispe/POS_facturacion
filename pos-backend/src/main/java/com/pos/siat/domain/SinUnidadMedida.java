package com.pos.siat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Catálogo paramétrico SIN: unidades de medida. */
@Entity
@Table(name = "sin_unidades_medida")
@Getter
@Setter
@NoArgsConstructor
public class SinUnidadMedida {

    @Id
    @Column(name = "codigo_unidad_sin")
    private Integer codigoUnidadSin;

    @Column(name = "descripcion", nullable = false, length = 100)
    private String descripcion;
}
