package com.pos.empresas.dto;

public record UsuarioEmpleadoResponse(
		Integer idUsuario,
		String email,
		Boolean activo,
		PersonaEmpleadoResponse persona) {
}
