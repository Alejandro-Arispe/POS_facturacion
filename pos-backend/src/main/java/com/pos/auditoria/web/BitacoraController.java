package com.pos.auditoria.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pos.auditoria.dto.BitacoraDtos;
import com.pos.auditoria.service.BitacoraService;
import com.pos.common.access.EmpresaAccess;
import com.pos.common.security.SecurityUtils;

/** Consulta de la bitacora de una empresa. Es de solo lectura: no existen endpoints para editar ni borrar. */
@RestController
@RequestMapping("/api/empresas")
public class BitacoraController {

	private final BitacoraService bitacoraService;
	private final EmpresaAccess empresaAccess;

	public BitacoraController(BitacoraService bitacoraService, EmpresaAccess empresaAccess) {
		this.bitacoraService = bitacoraService;
		this.empresaAccess = empresaAccess;
	}

	@GetMapping("/{idEmpresa}/bitacora")
	public BitacoraDtos.Pagina listar(
			@PathVariable Integer idEmpresa,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(required = false) String accion,
			@RequestParam(required = false) String modulo,
			@RequestParam(required = false) String usuario,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		empresaAccess.requirePermiso(SecurityUtils.currentUser(), idEmpresa, "BITACORA_VER");
		return bitacoraService.buscar(
				new BitacoraDtos.Filtro(idEmpresa, desde, hasta, accion, modulo, usuario, page, size), idEmpresa);
	}
}
