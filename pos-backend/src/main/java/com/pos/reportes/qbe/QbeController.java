package com.pos.reportes.qbe;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reportes/{idEmpresa}/qbe")
public class QbeController {

	private final QbeService qbeService;

	public QbeController(QbeService qbeService) {
		this.qbeService = qbeService;
	}

	@GetMapping("/fuentes")
	public List<QbeDtos.FuenteInfo> fuentes(@PathVariable Integer idEmpresa) {
		return qbeService.fuentes(idEmpresa);
	}

	@PostMapping("/ejecutar")
	public QbeDtos.Resultado ejecutar(@PathVariable Integer idEmpresa, @RequestBody QbeDtos.Consulta consulta) {
		return qbeService.ejecutar(idEmpresa, consulta);
	}
}
