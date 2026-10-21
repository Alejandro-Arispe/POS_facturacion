package com.pos.usuarios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "rol")
public class Rol {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_rol")
	private Integer idRol;

	@Column(name = "nombre", nullable = false, length = 100)
	private String nombre;

	@Column(name = "id_empresa")
	private Integer idEmpresa;

	@Column(name = "tipo", length = 50)
	private String tipo;

	@Column(name = "descripcion", nullable = false, length = 255)
	private String descripcion;

	@Column(name = "activo", nullable = false)
	private Boolean activo = Boolean.TRUE;
}
