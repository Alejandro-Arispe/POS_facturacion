package com.pos.empresas.dto;

import java.time.LocalDate;

public record SucursalResponse(
		Integer idSucursal,
		Integer idEmpresa,
		String nombre,
		String direccion,
		String telefono,
		String ciudad,
		LocalDate fechaRegistro,
		Boolean activo) {
}
