package com.pos.empresas.domain;

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
@Table(name = "configuracion_sistema")
public class ConfiguracionSistema {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_configuracion")
	private Integer idConfiguracion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_empresa", nullable = false, unique = true)
	private Empresa empresa;

	@Column(name = "tema", nullable = false, length = 50)
	private String tema = "claro";

	@Column(name = "idioma", nullable = false, length = 10)
	private String idioma = "es";

	@Column(name = "zona_horaria", nullable = false, length = 100)
	private String zonaHoraria = "America/La_Paz";

	@Column(name = "moneda", nullable = false, length = 20)
	private String moneda = "BOB";

	@Column(name = "activar_notificaciones_push", nullable = false)
	private Boolean activarNotificacionesPush = Boolean.TRUE;

	@Column(name = "activar_sonido", nullable = false)
	private Boolean activarSonido = Boolean.TRUE;

	@Column(name = "activar_vibracion", nullable = false)
	private Boolean activarVibracion = Boolean.TRUE;

	@Column(name = "confirmar_antes_de_eliminar", nullable = false)
	private Boolean confirmarAntesDeEliminar = Boolean.TRUE;

	@Column(name = "cerrar_sesion_por_inactividad", nullable = false)
	private Boolean cerrarSesionPorInactividad = Boolean.FALSE;

	@Column(name = "minutos_inactividad", nullable = false)
	private Integer minutosInactividad = 15;

	@Column(name = "imprimir_automaticamente", nullable = false)
	private Boolean imprimirAutomaticamente = Boolean.FALSE;

	@Column(name = "numero_copias", nullable = false)
	private Integer numeroCopias = 1;

	@Column(name = "tamano_ticket", nullable = false, length = 50)
	private String tamanoTicket = "80mm";

	@Column(name = "color_primario", length = 20)
	private String colorPrimario = "#047857";
}
