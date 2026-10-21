package com.pos.superadmin.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pos.auditoria.dto.BitacoraDtos;
import com.pos.auditoria.service.BitacoraService;
import com.pos.superadmin.dto.SuperAdminDtos;
import com.pos.superadmin.service.DemoDataService;
import com.pos.superadmin.service.SuperAdminService;

@RestController
@RequestMapping("/api/superadmin")
public class SuperAdminController {

	private final SuperAdminService superAdminService;
	private final BitacoraService bitacoraService;
	private final DemoDataService demoDataService;

	public SuperAdminController(
			SuperAdminService superAdminService,
			BitacoraService bitacoraService,
			DemoDataService demoDataService) {
		this.superAdminService = superAdminService;
		this.bitacoraService = bitacoraService;
		this.demoDataService = demoDataService;
	}

	@GetMapping("/resumen")
	public SuperAdminDtos.Resumen resumen() {
		return superAdminService.resumen();
	}

	@GetMapping("/empresas")
	public List<SuperAdminDtos.EmpresaInfo> empresas() {
		return superAdminService.empresas();
	}

	@PutMapping("/empresas/{idEmpresa}/estado")
	public SuperAdminDtos.EmpresaInfo cambiarEstadoEmpresa(
			@PathVariable Integer idEmpresa, @RequestBody SuperAdminDtos.CambiarEstado datos) {
		return superAdminService.cambiarEstadoEmpresa(idEmpresa, datos);
	}

	@PostMapping("/empresas/{idEmpresa}/suscripcion")
	public SuperAdminDtos.EmpresaInfo asignarSuscripcion(
			@PathVariable Integer idEmpresa, @RequestBody SuperAdminDtos.AsignarSuscripcion datos) {
		return superAdminService.asignarSuscripcion(idEmpresa, datos);
	}

	@DeleteMapping("/empresas/{idEmpresa}/suscripcion")
	public SuperAdminDtos.EmpresaInfo cancelarSuscripcion(@PathVariable Integer idEmpresa) {
		return superAdminService.cancelarSuscripcion(idEmpresa);
	}

	/** Genera un historial de ventas de demostracion (dias hacia atras desde hoy) para alimentar los reportes. */
	@PostMapping("/empresas/{idEmpresa}/datos-demo")
	public Map<String, Object> generarDatosDemo(
			@PathVariable Integer idEmpresa,
			@RequestParam(required = false) Integer dias) {
		return demoDataService.generar(idEmpresa, dias);
	}

	/** Elimina el historial de demostracion generado con el endpoint anterior. */
	@DeleteMapping("/empresas/{idEmpresa}/datos-demo")
	public Map<String, Object> eliminarDatosDemo(@PathVariable Integer idEmpresa) {
		return demoDataService.limpiar(idEmpresa);
	}

	@GetMapping("/planes")
	public List<SuperAdminDtos.PlanInfo> planes() {
		return superAdminService.planes();
	}

	@GetMapping("/modulos")
	public List<SuperAdminDtos.ModuloInfo> modulos() {
		return superAdminService.modulos();
	}

	@PostMapping("/planes")
	public SuperAdminDtos.PlanInfo crearPlan(@RequestBody SuperAdminDtos.PlanGuardar datos) {
		return superAdminService.crearPlan(datos);
	}

	@PutMapping("/planes/{idPlan}")
	public SuperAdminDtos.PlanInfo actualizarPlan(
			@PathVariable Integer idPlan, @RequestBody SuperAdminDtos.PlanGuardar datos) {
		return superAdminService.actualizarPlan(idPlan, datos);
	}

	@GetMapping("/usuarios")
	public List<SuperAdminDtos.UsuarioInfo> usuarios() {
		return superAdminService.usuarios();
	}

	@PutMapping("/usuarios/{idUsuario}/estado")
	public SuperAdminDtos.UsuarioInfo cambiarEstadoUsuario(
			@PathVariable Integer idUsuario, @RequestBody SuperAdminDtos.CambiarEstado datos) {
		return superAdminService.cambiarEstadoUsuario(idUsuario, datos);
	}

	@GetMapping("/bitacora")
	public BitacoraDtos.Pagina bitacora(
			@RequestParam(name = "id_empresa", required = false) Integer idEmpresa,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(required = false) String accion,
			@RequestParam(required = false) String modulo,
			@RequestParam(required = false) String usuario,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		superAdminService.requireSuperAdmin();
		return bitacoraService.buscar(
				new BitacoraDtos.Filtro(idEmpresa, desde, hasta, accion, modulo, usuario, page, size), null);
	}

	@GetMapping("/bitacora/verificar")
	public BitacoraDtos.Verificacion verificarBitacora() {
		superAdminService.requireSuperAdmin();
		return bitacoraService.verificar();
	}
}
