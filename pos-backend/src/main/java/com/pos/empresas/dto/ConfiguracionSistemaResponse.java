package com.pos.empresas.dto;

public record ConfiguracionSistemaResponse(
		Integer idConfiguracion,
		Integer idEmpresa,
		String tema,
		String idioma,
		String zonaHoraria,
		String moneda,
		Boolean activarNotificacionesPush,
		Boolean activarSonido,
		Boolean activarVibracion,
		Boolean confirmarAntesDeEliminar,
		Boolean cerrarSesionPorInactividad,
		Integer minutosInactividad,
		Boolean imprimirAutomaticamente,
		Integer numeroCopias,
		String tamanoTicket,
		String colorPrimario) {
}
