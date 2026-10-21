package com.pos.empresas.dto;

public record SucursalEmpleadoAsignadaResponse(
		Integer idUsuarioRol,
		Integer idUsuario,
		Integer idRol,
		Integer idEmpresa,
		Integer idSucursal,
		Boolean activo,
		EmpresaEmpleadoResponse empresa,
		SucursalResponse sucursal) {
}
