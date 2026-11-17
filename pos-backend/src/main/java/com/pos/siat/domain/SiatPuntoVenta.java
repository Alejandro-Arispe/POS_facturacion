package com.pos.siat.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Códigos SIN de un punto de venta: el CUIS se obtiene una vez; el CUFD vence a las 24 h de obtenido. */
@Entity
@Table(name = "siat_punto_venta", uniqueConstraints = @UniqueConstraint(
        name = "uk_siat_punto_venta", columnNames = {"nit_emisor", "id_sucursal", "id_punto_venta"}))
@Getter
@Setter
@NoArgsConstructor
public class SiatPuntoVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_siat_punto_venta")
    private Integer idSiatPuntoVenta;

    @Column(name = "nit_emisor", nullable = false, length = 20)
    private String nitEmisor;

    @Column(name = "id_sucursal", nullable = false)
    private Integer idSucursal;

    @Column(name = "id_punto_venta", nullable = false)
    private Integer idPuntoVenta;

    @Column(name = "cuis", nullable = false, length = 20)
    private String cuis;

    @Column(name = "fecha_cuis", nullable = false)
    private LocalDateTime fechaCuis;

    @Column(name = "cufd", length = 100)
    private String cufd;

    @Column(name = "cufd_codigo_control", length = 40)
    private String cufdCodigoControl;

    @Column(name = "cufd_obtenido")
    private LocalDateTime cufdObtenido;

    @Column(name = "cufd_vence")
    private LocalDateTime cufdVence;
}
