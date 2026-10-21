package com.pos.empresas.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
@Table(name = "caja_sesion")
public class CajaSesion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_caja_sesion")
	private Integer idCajaSesion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_caja", nullable = false)
	private Caja caja;

	@Column(name = "id_usuario", nullable = false)
	private Integer idUsuario;

	@Column(name = "fecha_apertura", nullable = false)
	private LocalDateTime fechaApertura;

	@Column(name = "fecha_cierre")
	private LocalDateTime fechaCierre;

	@Column(name = "monto_inicial", nullable = false, precision = 12, scale = 2)
	private BigDecimal montoInicial = BigDecimal.ZERO;

	@Column(name = "monto_final", precision = 12, scale = 2)
	private BigDecimal montoFinal;

	@Column(name = "estado", nullable = false, length = 20)
	private String estado = "Abierto";

	@Column(name = "nota", columnDefinition = "TEXT")
	private String nota;
}
