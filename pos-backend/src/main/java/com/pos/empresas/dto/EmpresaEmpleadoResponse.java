package com.pos.empresas.dto;

import java.time.LocalDate;

public record EmpresaEmpleadoResponse(
		Integer idEmpresa,
		String nombre,
		String razonSocial,
		String nit,
		String correo,
		LocalDate fechaCreacion,
		Boolean activo) {
}
