package com.pos.empresas.service;

import java.util.Locale;

/**
 * Regla unica para saber si un tipo de movimiento de caja suma o resta al efectivo esperado.
 * La usan el arqueo de caja y los reportes para que ambos den los mismos totales.
 */
public final class SignoMovimientoCaja {

	private SignoMovimientoCaja() {
	}

	/** 1 si el tipo suma (APERTURA, INGRESO, AJUSTE_POSITIVO), -1 si resta (EGRESO, AJUSTE_NEGATIVO), 0 si es CIERRE. */
	public static int signo(String nombreTipo) {
		String nombre = nombreTipo == null ? "" : nombreTipo.strip().toUpperCase(Locale.ROOT);
		if ("EGRESO".equals(nombre) || "AJUSTE_NEGATIVO".equals(nombre)) {
			return -1;
		}
		if ("CIERRE".equals(nombre)) {
			return 0;
		}
		return 1;
	}
}
