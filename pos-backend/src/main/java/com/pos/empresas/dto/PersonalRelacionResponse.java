package com.pos.empresas.dto;

public record PersonalRelacionResponse(
		Integer idUsuarioRol,
		Integer idRol,
		Integer idEmpresa,
		Integer idSucursal,
		Boolean activo) {
}
