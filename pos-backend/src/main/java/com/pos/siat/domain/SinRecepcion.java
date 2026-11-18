package com.pos.siat.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Registro propio del SIN simulado: lo que "Impuestos" recibió para cada CUF. */
@Entity
@Table(name = "sin_simulado_recepcion")
@Getter
@Setter
@NoArgsConstructor
public class SinRecepcion {

    @Id
    @Column(name = "cuf", length = 100)
    private String cuf;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @Column(name = "codigo_recepcion", nullable = false, length = 40)
    private String codigoRecepcion;

    @Column(name = "fecha_recepcion", nullable = false)
    private LocalDateTime fechaRecepcion;

    @Column(name = "mensaje", length = 500)
    private String mensaje;

    @Column(name = "codigo_motivo_anulacion")
    private Integer codigoMotivoAnulacion;
}
