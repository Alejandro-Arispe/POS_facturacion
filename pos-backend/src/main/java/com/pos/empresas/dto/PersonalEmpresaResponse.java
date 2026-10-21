package com.pos.empresas.dto;

public record PersonalEmpresaResponse(
		Integer idUsuarioRol,
		Integer idUsuario,
		Integer idRol,
		Integer idEmpresa,
		Integer idSucursal,
		Boolean activo,
		UsuarioEmpleadoResponse usuario) {
}
