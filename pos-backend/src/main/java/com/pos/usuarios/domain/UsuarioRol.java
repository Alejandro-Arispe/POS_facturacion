package com.pos.usuarios.domain;

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
@Table(name = "usuario_rol")
public class UsuarioRol {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_usuario_rol")
	private Integer idUsuarioRol;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_usuario", nullable = false)
	private Usuario usuario;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_rol", nullable = false)
	private Rol rol;

	@Column(name = "id_empresa", nullable = false)
	private Integer idEmpresa;

	@Column(name = "id_sucursal")
	private Integer idSucursal;

	@Column(name = "fecha", nullable = false)
	private LocalDate fecha;

	@Column(name = "activo", nullable = false)
	private Boolean activo = Boolean.TRUE;
}
