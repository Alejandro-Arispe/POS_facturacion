package com.pos.empresas.dto;

public record EmpresaCreadaResponse(
		EmpresaResponse empresa,
		UsuarioRolResumenResponse usuarioRol) {
}
