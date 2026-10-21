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
@Table(name = "caja")
public class Caja {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_caja")
	private Integer idCaja;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_sucursal", nullable = false)
	private Sucursal sucursal;

	@Column(name = "nombre", nullable = false, length = 150)
	private String nombre;

	@Column(name = "codigo", nullable = false, length = 50)
	private String codigo;

	@Column(name = "fecha_creacion", nullable = false)
	private LocalDate fechaCreacion;

	@Column(name = "activo", nullable = false)
	private Boolean activo = Boolean.TRUE;
}
