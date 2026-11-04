package com.pos.siat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Catálogo paramétrico SIN: motivos de anulación de factura. */
@Entity
@Table(name = "sin_motivos_anulacion")
@Getter
@Setter
@NoArgsConstructor
public class SinMotivoAnulacion {

    @Id
    @Column(name = "codigo_motivo_sin")
    private Integer codigoMotivoSin;

    @Column(name = "descripcion", nullable = false, length = 150)
    private String descripcion;
}
