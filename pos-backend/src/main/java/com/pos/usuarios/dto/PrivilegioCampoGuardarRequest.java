package com.pos.usuarios.dto;

import java.util.List;

public record PrivilegioCampoGuardarRequest(String formulario, List<CampoEstadoRequest> campos) {
}
