package com.pos.empresas.domain;

import java.math.BigDecimal;

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
@Table(name = "caja_cierre_detalle")
public class CajaCierreDetalle {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_caja_cierre_detalle")
	private Integer idCajaCierreDetalle;

	@Column(name = "id_metodo_pago", nullable = false)
	private Integer idMetodoPago;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_caja_sesion", nullable = false)
	private CajaSesion cajaSesion;

	@Column(name = "monto_esperado", nullable = false, precision = 12, scale = 2)
	private BigDecimal montoEsperado = BigDecimal.ZERO;

	@Column(name = "monto_real", nullable = false, precision = 12, scale = 2)
	private BigDecimal montoReal = BigDecimal.ZERO;

	@Column(name = "diferencia", nullable = false, precision = 12, scale = 2)
	private BigDecimal diferencia = BigDecimal.ZERO;

	@Column(name = "observacion", columnDefinition = "TEXT")
	private String observacion;
}
