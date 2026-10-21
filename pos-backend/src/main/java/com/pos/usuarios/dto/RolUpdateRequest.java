package com.pos.usuarios.dto;

import java.util.List;

public record RolUpdateRequest(Boolean activo, List<Integer> permisoIds) {
}
