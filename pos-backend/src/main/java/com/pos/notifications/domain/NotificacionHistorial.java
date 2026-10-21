package com.pos.notifications.domain;

import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(name = "notificaciones_historial")
@Getter
@Setter
@NoArgsConstructor
public class NotificacionHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_empresa")
    private Integer idEmpresa;

    @Column(name = "prioridad", nullable = false)
    private Integer prioridad = 0;

    @Column(name = "tipo", nullable = false)
    private String tipo;

    @Column(name = "titulo", nullable = false)
    private String titulo;

    @Column(name = "mensaje", nullable = false)
    private String mensaje;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private Map<String, Object> payload;

    @Column(name = "leido")
    private Boolean leido = Boolean.FALSE;

    @Column(name = "fecha")
    private LocalDateTime fecha;
}
