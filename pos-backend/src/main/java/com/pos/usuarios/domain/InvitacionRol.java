package com.pos.usuarios.domain;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
@Table(name="invitacion_rol")
public class InvitacionRol {
    @Id @Column(length=36) private String id;
    @Column(name="id_empresa",nullable=false) private Integer idEmpresa;
    @Column(name="id_usuario",nullable=false) private Integer idUsuario;
    @Column(name="id_emisor",nullable=false) private Integer idEmisor;
    @Column(name="id_rol",nullable=false) private Integer idRol;
    @Column(name="expira_en",nullable=false) private Instant expiraEn;
    @Column(nullable=false) private boolean consumida;
    @Column(nullable=false) private boolean revocada;
}
