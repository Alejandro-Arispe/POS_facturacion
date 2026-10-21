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
@Table(name = "historial_suscripcion")
public class HistorialSuscripcion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_historial_suscripcion")
	private Integer idHistorialSuscripcion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_plan", nullable = false)
	private Plan plan;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_empresa", nullable = false)
	private Empresa empresa;

	@Column(name = "fecha_inicio", nullable = false)
	private LocalDate fechaInicio;

	@Column(name = "fecha_fin")
	private LocalDate fechaFin;

	@Column(name = "estado", nullable = false, length = 50)
	private String estado;

	@Column(name = "stripe_session_id", unique = true, length = 255)
	private String stripeSessionId;

	@Column(name = "stripe_payment_intent_id", length = 255)
	private String stripePaymentIntentId;

	@Column(name = "stripe_payment_status", length = 50)
	private String stripePaymentStatus;
}
