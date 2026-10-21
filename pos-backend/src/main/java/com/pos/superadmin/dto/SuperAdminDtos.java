package com.pos.superadmin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class SuperAdminDtos {

	private SuperAdminDtos() {
	}

	public record Resumen(
			long totalEmpresas,
			long empresasActivas,
			long empresasConPlan,
			long totalUsuarios,
			long usuariosActivos,
			BigDecimal ingresosMensuales,
			long eventosBitacoraHoy,
			long eventosBitacoraTotal) {
	}

	public record SuscripcionInfo(
			Integer idHistorialSuscripcion,
			Integer idPlan,
			String planNombre,
			String estado,
			LocalDate fechaInicio,
			LocalDate fechaFin) {
	}

	public record EmpresaInfo(
			Integer idEmpresa,
			String nombre,
			String razonSocial,
			String nit,
			String correo,
			LocalDate fechaCreacion,
			Boolean activo,
			List<String> administradores,
			long sucursales,
			SuscripcionInfo suscripcionActiva) {
	}

	public record CambiarEstado(Boolean activo) {
	}

	public record AsignarSuscripcion(Integer idPlan, Integer meses) {
	}

	public record PlanModuloInfo(Integer idModulo, String codigo, String nombre, String configuracion) {
	}

	public record PlanInfo(
			Integer idPlan,
			String nombre,
			String descripcion,
			BigDecimal precio,
			List<PlanModuloInfo> modulos,
			long empresasSuscritas) {
	}

	public record PlanGuardar(
			String nombre,
			String descripcion,
			BigDecimal precio,
			List<String> modulos,
			String configuracion) {
	}

	public record ModuloInfo(Integer idModulo, String codigo, String nombre) {
	}

	public record UsuarioInfo(
			Integer idUsuario,
			String email,
			String nombreCompleto,
			LocalDate fechaCreacion,
			Boolean activo,
			Boolean superAdmin,
			List<String> empresas) {
	}
}
