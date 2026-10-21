package com.pos.auditoria.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registro de auditoria de solo escritura. Cada fila guarda el hash de la anterior,
 * formando una cadena que permite detectar cualquier modificacion o borrado.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Immutable
@Table(name = "bitacora", indexes = {
		@Index(name = "ix_bitacora_empresa_fecha", columnList = "id_empresa, fecha"),
		@Index(name = "ix_bitacora_fecha", columnList = "fecha")
})
public class Bitacora {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_bitacora")
	private Long idBitacora;

	@Column(name = "fecha", nullable = false)
	private LocalDateTime fecha;

	@Column(name = "id_usuario")
	private Integer idUsuario;

	@Column(name = "email_usuario", length = 255)
	private String emailUsuario;

	@Column(name = "id_empresa")
	private Integer idEmpresa;

	@Column(name = "accion", nullable = false, length = 40)
	private String accion;

	@Column(name = "modulo", length = 60)
	private String modulo;

	@Column(name = "metodo", length = 10)
	private String metodo;

	@Column(name = "ruta", length = 255)
	private String ruta;

	@Column(name = "estado_http")
	private Integer estadoHttp;

	@Column(name = "ip", length = 64)
	private String ip;

	@Column(name = "user_agent", length = 255)
	private String userAgent;

	@Column(name = "detalle", length = 500)
	private String detalle;

	@Column(name = "hash_anterior", nullable = false, length = 64)
	private String hashAnterior;

	@Column(name = "hash", nullable = false, length = 64)
	private String hash;
}
