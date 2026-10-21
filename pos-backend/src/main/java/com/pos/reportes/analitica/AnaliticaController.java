package com.pos.reportes.analitica;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reportes/{idEmpresa}/analitica")
public class AnaliticaController {

	private final AnaliticaService analiticaService;

	public AnaliticaController(AnaliticaService analiticaService) {
		this.analiticaService = analiticaService;
	}

	@GetMapping
	public Map<String, Object> analizar(
			@PathVariable Integer idEmpresa,
			@RequestParam(required = false) Integer dias,
			@RequestParam(name = "id_sucursal", required = false) Integer idSucursal) {
		return analiticaService.analizar(idEmpresa, dias, idSucursal);
	}
}
