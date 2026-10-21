package com.pos.usuarios.dto;

import java.util.List;

public record RolCreateRequest(String nombre, List<Integer> permisoIds) {
}
