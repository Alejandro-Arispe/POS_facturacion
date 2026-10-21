package com.pos.common.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Hibernate guarda las fechas en UTC (hibernate.jdbc.time_zone=UTC). Las consultas SQL directas
 * deben convertir igual que Hibernate para que las horas coincidan con las del resto del sistema.
 */
public final class UtcTimestamps {

	private static final ZoneId ZONA_LOCAL = ZoneId.systemDefault();

	private UtcTimestamps() {
	}

	private static Calendar utc() {
		return Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC));
	}

	/** Lee una columna timestamp guardada en UTC y la devuelve en la hora local del servidor. */
	public static LocalDateTime leer(ResultSet rs, int columna) throws SQLException {
		Timestamp ts = rs.getTimestamp(columna, utc());
		return ts == null ? null : ts.toLocalDateTime();
	}

	public static LocalDateTime leer(ResultSet rs, String columna) throws SQLException {
		Timestamp ts = rs.getTimestamp(columna, utc());
		return ts == null ? null : ts.toLocalDateTime();
	}

	/** Convierte una hora local en el valor UTC con el que se compara en la base de datos. */
	public static Timestamp parametro(LocalDateTime local) {
		return Timestamp.valueOf(local.atZone(ZONA_LOCAL).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
	}
}
