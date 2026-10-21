package com.pos.usuarios.dto;

import java.util.List;

public record RolCreateResponse(RolResponse rol, List<Integer> permisoIds) {
}
