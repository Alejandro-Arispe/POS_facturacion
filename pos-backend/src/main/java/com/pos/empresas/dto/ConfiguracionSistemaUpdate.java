package com.pos.empresas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConfiguracionSistemaUpdate(
		@NotBlank String tema,
		@NotBlank String idioma,
		@NotBlank String zonaHoraria,
		@NotBlank String moneda,
		@NotNull Boolean activarNotificacionesPush,
		@NotNull Boolean activarSonido,
		@NotNull Boolean activarVibracion,
		@NotNull Boolean confirmarAntesDeEliminar,
		@NotNull Boolean cerrarSesionPorInactividad,
		@NotNull Integer minutosInactividad,
		@NotNull Boolean imprimirAutomaticamente,
		@NotNull Integer numeroCopias,
		@NotBlank String tamanoTicket,
		String colorPrimario) {
}
