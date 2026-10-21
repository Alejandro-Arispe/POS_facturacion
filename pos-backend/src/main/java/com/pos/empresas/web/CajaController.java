package com.pos.empresas.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pos.empresas.dto.CajaCierreDetalleCreate;
import com.pos.empresas.dto.CajaSesionCierreResponse;
import com.pos.empresas.dto.CajaSesionCreate;
import com.pos.empresas.dto.CajaSesionResponse;
import com.pos.empresas.dto.MovimientoCajaCreate;
import com.pos.empresas.dto.MovimientoCajaResponse;
import com.pos.empresas.dto.ResumenMovimientosCajaResponse;
import com.pos.empresas.service.CajaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cajas")
public class CajaController {

	private final CajaService cajaService;

	public CajaController(CajaService cajaService) {
		this.cajaService = cajaService;
	}

	@PostMapping("/{idCaja}/sesiones")
	public CajaSesionResponse crearSesion(
			@PathVariable Integer idCaja,
			@Valid @RequestBody CajaSesionCreate datos) {
		return cajaService.crearSesion(idCaja, datos);
	}

	@PostMapping("/sesiones/{idCajaSesion}/movimientos")
	public MovimientoCajaResponse crearMovimiento(
			@PathVariable Integer idCajaSesion,
			@Valid @RequestBody MovimientoCajaCreate datos) {
		return cajaService.crearMovimiento(idCajaSesion, datos);
	}

	@GetMapping("/sesiones/{idCajaSesion}/movimientos")
	public List<MovimientoCajaResponse> listarMovimientos(@PathVariable Integer idCajaSesion) {
		return cajaService.listarMovimientos(idCajaSesion);
	}

	@GetMapping("/sesiones/{idCajaSesion}/movimientos/resumen")
	public ResumenMovimientosCajaResponse resumenMovimientos(@PathVariable Integer idCajaSesion) {
		return cajaService.resumenMovimientos(idCajaSesion);
	}

	@PostMapping("/sesiones/{idCajaSesion}/cierres")
	public CajaSesionCierreResponse cerrarSesion(
			@PathVariable Integer idCajaSesion,
			@Valid @RequestBody List<@Valid CajaCierreDetalleCreate> datos) {
		return cajaService.cerrarSesion(idCajaSesion, datos);
	}
}
