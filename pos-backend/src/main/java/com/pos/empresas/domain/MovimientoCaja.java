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
@Table(name = "movimiento_caja")
public class MovimientoCaja {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_movimiento_caja")
	private Integer idMovimientoCaja;

	@Column(name = "id_metodo_pago")
	private Integer idMetodoPago;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_tipo_movimiento_caja", nullable = false)
	private TipoMovimientoCaja tipoMovimientoCaja;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_caja_sesion", nullable = false)
	private CajaSesion cajaSesion;

	@Column(name = "id_usuario", nullable = false)
	private Integer idUsuario;

	@Column(name = "fecha", nullable = false)
	private LocalDateTime fecha;

	@Column(name = "monto", nullable = false, precision = 12, scale = 2)
	private BigDecimal monto = BigDecimal.ZERO;

	@Column(name = "concepto", columnDefinition = "TEXT")
	private String concepto;
}
