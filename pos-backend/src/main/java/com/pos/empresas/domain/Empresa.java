package com.pos.empresas.domain;

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
@Table(name = "empresa")
public class Empresa {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_empresa")
	private Integer idEmpresa;

	@Column(name = "nombre", nullable = false, length = 150)
	private String nombre;

	@Column(name = "razon_social", nullable = false, length = 200)
	private String razonSocial;

	@Column(name = "nit", nullable = false, unique = true, length = 30)
	private String nit;

	@Column(name = "correo", nullable = false, unique = true, length = 255)
	private String correo;

	@Column(name = "fecha_creacion", nullable = false)
	private LocalDate fechaCreacion;

	@Column(name = "activo", nullable = false)
	private Boolean activo = Boolean.TRUE;
}
