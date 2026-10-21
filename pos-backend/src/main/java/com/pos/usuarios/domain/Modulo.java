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
@Table(name = "modulo")
public class Modulo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_modulo")
	private Integer idModulo;

	@Column(name = "codigo", nullable = false, unique = true, length = 100)
	private String codigo;

	@Column(name = "nombre", nullable = false, length = 150)
	private String nombre;
}
