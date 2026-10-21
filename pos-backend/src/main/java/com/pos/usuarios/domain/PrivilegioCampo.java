package com.pos.usuarios.domain;

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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "privilegio_campo",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_privilegio_campo_rol_formulario_campo",
                columnNames = {"id_rol", "formulario", "campo"}
        )
)
public class PrivilegioCampo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "id_rol", nullable = false)
    private Integer idRol;

    @Column(name = "formulario", nullable = false, length = 40)
    private String formulario;

    @Column(name = "campo", nullable = false, length = 40)
    private String campo;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;
}
