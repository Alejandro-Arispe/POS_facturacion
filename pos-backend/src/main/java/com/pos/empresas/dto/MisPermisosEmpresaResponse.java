package com.pos.empresas.dto;

import java.util.List;

public record MisPermisosEmpresaResponse(
		List<PermisoConRolResponse> permisos,
		SuscripcionActivaDetalleResponse suscripcionActiva) {
}
