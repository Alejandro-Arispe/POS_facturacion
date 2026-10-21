package com.pos.empresas.dto;

import java.time.LocalDate;

public record EmpresaResponse(
		Integer idEmpresa,
		String nombre,
		String razonSocial,
		String nit,
		String correo,
		LocalDate fechaCreacion,
		Boolean activo,
		SuscripcionActivaResumenResponse suscripcionActiva) {
}
