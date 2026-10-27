package com.pos.usuarios.domain;

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
@Table(name = "permiso")
public class Permiso {
    @Column(name = "codigo_canonico", unique = true, length = 150)
    private String codigoCanonico;
    @Column(length = 60) private String recurso;
    @Column(length = 50) private String accion;
    @Column(length = 60) private String atributo;
    @Column(length = 255) private String descripcion;
    @org.hibernate.annotations.ColumnDefault("true")
    @Column(nullable = false) private Boolean activo = true;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_permiso")
	private Integer idPermiso;

	@Column(name = "codigo", nullable = false, length = 100)
	private String codigo;

	@Column(name = "nombre", nullable = false, length = 150)
	private String nombre;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_modulo", nullable = false)
	private Modulo modulo;
}
