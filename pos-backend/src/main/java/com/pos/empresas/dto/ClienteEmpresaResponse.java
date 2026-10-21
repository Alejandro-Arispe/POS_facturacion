package com.pos.empresas.dto;

public record ClienteEmpresaResponse(
		Integer idUsuarioRol,
		Integer idUsuario,
		Integer idRol,
		Integer idEmpresa,
		Integer idSucursal,
		Boolean activo,
		UsuarioEmpleadoResponse usuario,
		ClienteResponse cliente) {
}
