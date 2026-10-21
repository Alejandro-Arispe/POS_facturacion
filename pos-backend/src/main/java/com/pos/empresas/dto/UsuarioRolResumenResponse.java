package com.pos.empresas.dto;

public record UsuarioRolResumenResponse(
		Integer idUsuarioRol,
		Integer idUsuario,
		Integer idRol,
		Integer idEmpresa,
		Integer idSucursal,
		Boolean activo) {
}
