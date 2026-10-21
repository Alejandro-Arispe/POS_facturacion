package com.pos.notifications.domain;

import java.time.LocalDateTime;

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
@Table(name = "dispositivos_tokens")
@Getter
@Setter
@NoArgsConstructor
public class DispositivoToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "token", nullable = false, unique = true)
    private String token;

    @Column(name = "uid_usuario")
    private String uidUsuario;

    @Column(name = "rol")
    private String rol;

    @Column(name = "plataforma")
    private String plataforma;

    @Column(name = "id_empresa")
    private Integer idEmpresa;

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro;
}
