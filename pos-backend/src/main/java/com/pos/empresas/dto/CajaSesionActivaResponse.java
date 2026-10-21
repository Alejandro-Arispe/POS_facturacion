package com.pos.empresas.dto;

public record CajaSesionActivaResponse(
		Integer idCajaSesion,
		Integer idUsuario,
		String nombreUsuario,
		String fechaApertura,
		Boolean esUsuarioActual) {
}
