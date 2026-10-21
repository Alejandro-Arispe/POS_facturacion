package com.pos.empresas.dto;

public record PermisoConRolResponse(
		Integer idPermiso,
		String codigo,
		String nombre,
		Integer idModulo,
		ModuloResponse modulo,
		Boolean activoRolPermiso) {
}
