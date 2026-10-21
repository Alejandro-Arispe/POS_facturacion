package com.pos.empresas.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvitacionEnviadaResponse(
		String mensaje,
		String email,
		String linkInvitacion,
		List<Integer> idSucursales,
		Integer idRol) {
}
