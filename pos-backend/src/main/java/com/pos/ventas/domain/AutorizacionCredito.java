package com.pos.ventas.domain;

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

/** HU-09: autorización de un administrador para una venta a crédito que excede el límite del cliente. */
@Entity
@Table(name = "autorizacion_credito")
@Getter
@Setter
@NoArgsConstructor
public class AutorizacionCredito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_autorizacion_credito")
    private Integer idAutorizacionCredito;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_venta", nullable = false)
    private Venta venta;

    @Column(name = "id_usuario_admin", nullable = false)
    private Integer idUsuarioAdmin;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "motivo", nullable = false, length = 500)
    private String motivo;
}
