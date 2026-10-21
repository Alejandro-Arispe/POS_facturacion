package com.pos.empresas.dto;

import java.util.List;

public record PersonalEmpresaAgrupadoResponse(
		Integer idUsuario,
		UsuarioEmpleadoResponse usuario,
		List<PersonalRelacionResponse> relaciones) {
}
