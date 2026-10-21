package com.pos.auditoria.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.pos.auditoria.domain.Bitacora;

public final class BitacoraDtos {

	private BitacoraDtos() {
	}

	public record Filtro(
			Integer idEmpresa,
			LocalDate desde,
			LocalDate hasta,
			String accion,
			String modulo,
			String usuario,
			Integer page,
			Integer size) {
	}

	public record Registro(
			Long idBitacora,
			LocalDateTime fecha,
			Integer idUsuario,
			String emailUsuario,
			Integer idEmpresa,
			String accion,
			String modulo,
			String metodo,
			String ruta,
			Integer estadoHttp,
			String ip,
			String detalle,
			String hash) {

		public static Registro from(Bitacora b) {
			return new Registro(
					b.getIdBitacora(),
					b.getFecha(),
					b.getIdUsuario(),
					b.getEmailUsuario(),
					b.getIdEmpresa(),
					b.getAccion(),
					b.getModulo(),
					b.getMetodo(),
					b.getRuta(),
					b.getEstadoHttp(),
					b.getIp(),
					b.getDetalle(),
					b.getHash());
		}
	}

	public record Pagina(List<Registro> items, long total, int page, int size) {
	}

	public record Verificacion(boolean integra, int totalRegistros, List<Long> registrosAlterados, String mensaje) {
	}
}
