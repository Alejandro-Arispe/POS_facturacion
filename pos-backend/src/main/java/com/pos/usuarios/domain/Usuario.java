package com.pos.usuarios.domain;

import java.time.LocalDate;
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
@Table(name = "usuario")
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_usuario")
	private Integer idUsuario;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_persona", nullable = false, unique = true)
	private Persona persona;

	@Column(name = "fecha_creacion", nullable = false)
	private LocalDate fechaCreacion;

	@Column(name = "email", nullable = false, unique = true, length = 255)
	private String email;

	@Column(name = "contrasena", nullable = false, length = 255)
	private String contrasena;

	@Column(name = "activo", nullable = false)
	private Boolean activo = Boolean.FALSE;

	@Column(name = "codigo_verificacion_hash", length = 64)
	private String codigoVerificacionHash;

	@Column(name = "codigo_verificacion_expira_en")
	private LocalDateTime codigoVerificacionExpiraEn;

	@Column(name = "codigo_verificacion_intentos", nullable = false)
	private Integer codigoVerificacionIntentos = 0;

	@Column(name = "super_admin")
	private Boolean superAdmin = Boolean.FALSE;

	public boolean esSuperAdmin() {
		return Boolean.TRUE.equals(superAdmin);
	}
}
