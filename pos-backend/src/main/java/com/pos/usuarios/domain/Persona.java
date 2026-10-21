package com.pos.usuarios.domain;

import java.time.LocalDate;

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
@Table(name = "persona")
public class Persona {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_persona")
	private Integer idPersona;

	@Column(name = "nombre_completo", nullable = false, length = 150)
	private String nombreCompleto;

	@Column(name = "fecha_nacimiento", nullable = false)
	private LocalDate fechaNacimiento;

	@Column(name = "genero", nullable = false, length = 20)
	private String genero;

	@Column(name = "telefono", nullable = false, length = 20)
	private String telefono;

	@Column(name = "documento", nullable = false, unique = true, length = 30)
	private String documento;
}
