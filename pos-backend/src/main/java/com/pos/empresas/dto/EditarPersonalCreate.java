package com.pos.empresas.dto;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record EditarPersonalCreate(
		@NotBlank @Email String email,
		@NotEmpty List<Integer> idSucursales,
		@NotNull Integer idRol,
		@NotNull Boolean activo) {
}
