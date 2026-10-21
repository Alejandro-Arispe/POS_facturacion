package com.pos.empresas.domain;

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
@Table(name = "tipo_movimiento_caja")
public class TipoMovimientoCaja {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_tipo_movimiento_caja")
	private Integer idTipoMovimientoCaja;

	@Column(name = "nombre", nullable = false, unique = true, length = 100)
	private String nombre;

	@Column(name = "descripcion", columnDefinition = "TEXT")
	private String descripcion;
}
