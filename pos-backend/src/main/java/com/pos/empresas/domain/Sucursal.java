package com.pos.empresas.domain;

import java.time.LocalDate;

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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sucursal")
public class Sucursal {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_sucursal")
	private Integer idSucursal;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_empresa", nullable = false)
	private Empresa empresa;

	@Column(name = "nombre", nullable = false, length = 150)
	private String nombre;

	@Column(name = "direccion", nullable = false, length = 255)
	private String direccion;

	@Column(name = "telefono", nullable = false, length = 30)
	private String telefono;

	@Column(name = "ciudad", nullable = false, length = 100)
	private String ciudad;

	@Column(name = "fecha_registro", nullable = false)
	private LocalDate fechaRegistro;

	@Column(name = "activo", nullable = false)
	private Boolean activo = Boolean.TRUE;
}
