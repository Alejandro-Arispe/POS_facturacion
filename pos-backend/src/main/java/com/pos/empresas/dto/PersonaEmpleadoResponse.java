package com.pos.empresas.dto;

import java.time.LocalDate;

public record PersonaEmpleadoResponse(
		Integer idPersona,
		String nombreCompleto,
		LocalDate fechaNacimiento,
		String genero,
		String telefono,
		String documento) {
}
