package com.pos.empresas.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pos.empresas.dto.CajaCreate;
import com.pos.empresas.dto.CajaResponse;
import com.pos.empresas.dto.SucursalEmpleadoAsignadaResponse;
import com.pos.empresas.dto.SucursalResponse;
import com.pos.empresas.dto.SucursalUpdate;
import com.pos.empresas.service.CajaService;
import com.pos.empresas.service.SucursalService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/sucursales")
public class SucursalController {

	private final SucursalService sucursalService;
	private final CajaService cajaService;

	public SucursalController(SucursalService sucursalService, CajaService cajaService) {
		this.sucursalService = sucursalService;
		this.cajaService = cajaService;
	}

	@PutMapping("/{idSucursal}")
	public SucursalResponse actualizar(
			@PathVariable Integer idSucursal,
			@Valid @RequestBody SucursalUpdate datos) {
		return sucursalService.actualizar(idSucursal, datos);
	}

	@GetMapping("/mis-sucursales-empleado/{idEmpresa}")
	public List<SucursalEmpleadoAsignadaResponse> misSucursalesEmpleado(@PathVariable Integer idEmpresa) {
		return sucursalService.misSucursalesEmpleado(idEmpresa);
	}

	@PostMapping("/{idSucursal}/cajas")
	public CajaResponse crearCaja(
			@PathVariable Integer idSucursal,
			@Valid @RequestBody CajaCreate datos) {
		return cajaService.crear(idSucursal, datos);
	}
}
