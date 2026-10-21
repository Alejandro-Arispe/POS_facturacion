package com.pos.empresas.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pos.empresas.dto.ConfiguracionSistemaResponse;
import com.pos.empresas.dto.ConfiguracionSistemaUpdate;
import com.pos.empresas.service.ConfiguracionSistemaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/configuracion-sistema")
public class ConfiguracionSistemaController {

	private final ConfiguracionSistemaService configuracionSistemaService;

	public ConfiguracionSistemaController(ConfiguracionSistemaService configuracionSistemaService) {
		this.configuracionSistemaService = configuracionSistemaService;
	}

	@GetMapping("/empresas/{idEmpresa}")
	public ConfiguracionSistemaResponse obtener(@PathVariable Integer idEmpresa) {
		return configuracionSistemaService.obtener(idEmpresa);
	}

	@PutMapping("/empresas/{idEmpresa}")
	public ConfiguracionSistemaResponse actualizar(
			@PathVariable Integer idEmpresa,
			@Valid @RequestBody ConfiguracionSistemaUpdate datos) {
		return configuracionSistemaService.actualizar(idEmpresa, datos);
	}
}
