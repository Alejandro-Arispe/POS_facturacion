package com.pos.respaldos;

import com.pos.empresas.domain.Empresa;
import com.pos.empresas.domain.Sucursal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "configuracion_respaldo")
public class ConfiguracionRespaldo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_configuracion_respaldo")
    private Integer idConfiguracionRespaldo;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_empresa", nullable = false, unique = true)
    private Empresa empresa;

    @Column(name = "activo", nullable = false)
    private Boolean activo = Boolean.FALSE;

    /**
     * Frecuencia de respaldo: DESACTIVADO, DIARIO, SEMANAL, MENSUAL
     */
    @Column(name = "frecuencia", nullable = false, length = 20)
    private String frecuencia = "DESACTIVADO";

    /**
     * Hora programada en formato HH:mm (ej. "02:00", "23:00")
     */
    @Column(name = "hora", nullable = false, length = 10)
    private String hora = "02:00";

    /**
     * Alcance: EMPRESA_COMPLETA o SUCURSAL
     */
    @Column(name = "alcance", nullable = false, length = 30)
    private String alcance = "EMPRESA_COMPLETA";

    /**
     * Si alcance es SUCURSAL, la sucursal específica a respaldar (opcional)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sucursal")
    private Sucursal sucursal;

    /**
     * Correo de destino para envío a la nube / buzón (exclusivo Plan Pro)
     */
    @Column(name = "email_destino", length = 150)
    private String emailDestino;

    /**
     * Indicador si debe enviar la copia adjunta por correo
     */
    @Column(name = "enviar_email", nullable = false)
    private Boolean enviarEmail = Boolean.FALSE;

    @Column(name = "ultimo_respaldo_exitoso")
    private java.time.LocalDateTime ultimoRespaldoExitoso;
}

