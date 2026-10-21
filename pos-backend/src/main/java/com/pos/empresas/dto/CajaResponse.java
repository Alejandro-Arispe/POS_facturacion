package com.pos.empresas.dto;

import java.time.LocalDate;

public record CajaResponse(
		Integer idCaja,
		Integer idSucursal,
		String nombre,
		String codigo,
		LocalDate fechaCreacion,
		Boolean activo,
		String estadoOperativo,
		CajaSesionActivaResponse sesionActiva) {

	public CajaResponse(
			Integer idCaja,
			Integer idSucursal,
			String nombre,
			String codigo,
			LocalDate fechaCreacion,
			Boolean activo) {
		this(idCaja, idSucursal, nombre, codigo, fechaCreacion, activo, null, null);
	}
}
