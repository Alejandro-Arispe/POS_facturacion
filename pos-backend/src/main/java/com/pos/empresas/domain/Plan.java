package com.pos.empresas.domain;

import java.math.BigDecimal;

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
@Table(name = "plan")
public class Plan {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_plan")
	private Integer idPlan;

	@Column(name = "nombre", nullable = false, length = 150)
	private String nombre;

	@Column(name = "descripcion", nullable = false, length = 255)
	private String descripcion;

	@Column(name = "precio", nullable = false, precision = 12, scale = 2)
	private BigDecimal precio = BigDecimal.ZERO;
}
