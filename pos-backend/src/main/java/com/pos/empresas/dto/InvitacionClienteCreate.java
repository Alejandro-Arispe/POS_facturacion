package com.pos.empresas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InvitacionClienteCreate(
		@NotBlank @Email String email) {
}
