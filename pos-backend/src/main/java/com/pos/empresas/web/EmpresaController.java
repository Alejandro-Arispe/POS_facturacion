package com.pos.empresas.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pos.empresas.dto.CajaResponse;
import com.pos.empresas.dto.CajaUpdate;
import com.pos.empresas.dto.ClienteEmpresaResponse;
import com.pos.empresas.dto.EditarPersonalCreate;
import com.pos.empresas.dto.EmpresaCreadaResponse;
import com.pos.empresas.dto.EmpresaCreate;
import com.pos.empresas.dto.EmpresaResponse;
import com.pos.empresas.dto.EmpresaUpdate;
import com.pos.empresas.dto.InvitacionClienteCreate;
import com.pos.empresas.dto.InvitacionEmpleadoCreate;
import com.pos.empresas.dto.InvitacionEnviadaResponse;
import com.pos.empresas.dto.MisPermisosEmpresaResponse;
import com.pos.empresas.dto.PersonalEmpresaAgrupadoResponse;
import com.pos.empresas.dto.PersonalEmpresaResponse;
import com.pos.empresas.dto.SucursalCreate;
import com.pos.empresas.dto.SucursalResponse;
import com.pos.empresas.service.CajaService;
import com.pos.empresas.service.EmpresaService;
import com.pos.empresas.service.SucursalService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/empresas")
public class EmpresaController {

	private final EmpresaService empresaService;
	private final SucursalService sucursalService;
	private final CajaService cajaService;

	public EmpresaController(
			EmpresaService empresaService,
			SucursalService sucursalService,
			CajaService cajaService) {
		this.empresaService = empresaService;
		this.sucursalService = sucursalService;
		this.cajaService = cajaService;
	}

	@PostMapping("/crear")
	public EmpresaCreadaResponse crear(@Valid @RequestBody EmpresaCreate datos) {
		return empresaService.crear(datos);
	}

	@GetMapping("/mis-empresas")
	public List<EmpresaResponse> misEmpresas() {
		return empresaService.misEmpresas();
	}

	@GetMapping("/mis-empresas-empleado")
	public List<EmpresaResponse> misEmpresasEmpleado() {
		return empresaService.misEmpresasEmpleado();
	}

	@GetMapping("/{idEmpresa}/mis-permisos")
	public MisPermisosEmpresaResponse misPermisos(@PathVariable Integer idEmpresa) {
		return empresaService.misPermisos(idEmpresa);
	}

	@GetMapping("/{idEmpresa}")
	public EmpresaResponse obtener(@PathVariable Integer idEmpresa) {
		return empresaService.obtener(idEmpresa);
	}

	@PutMapping("/{idEmpresa}")
	public EmpresaResponse actualizar(
			@PathVariable Integer idEmpresa,
			@Valid @RequestBody EmpresaUpdate datos) {
		return empresaService.actualizar(idEmpresa, datos);
	}

	@PostMapping("/{idEmpresa}/sucursales")
	public SucursalResponse crearSucursal(
			@PathVariable Integer idEmpresa,
			@Valid @RequestBody SucursalCreate datos) {
		return sucursalService.crear(idEmpresa, datos);
	}

	@GetMapping("/{idEmpresa}/sucursales")
	public List<SucursalResponse> listarSucursales(@PathVariable Integer idEmpresa) {
		return sucursalService.listar(idEmpresa);
	}

	@GetMapping("/{idEmpresa}/sucursales/{idSucursal}")
	public SucursalResponse obtenerSucursal(
			@PathVariable Integer idEmpresa,
			@PathVariable Integer idSucursal) {
		return sucursalService.obtener(idEmpresa, idSucursal);
	}

	@GetMapping("/{idEmpresa}/sucursales/{idSucursal}/cajas")
	public List<CajaResponse> listarCajas(
			@PathVariable Integer idEmpresa,
			@PathVariable Integer idSucursal) {
		return cajaService.listar(idEmpresa, idSucursal);
	}

	@GetMapping("/{idEmpresa}/sucursales/{idSucursal}/cajas/{idCaja}")
	public CajaResponse obtenerCaja(
			@PathVariable Integer idEmpresa,
			@PathVariable Integer idSucursal,
			@PathVariable Integer idCaja) {
		return cajaService.obtener(idEmpresa, idSucursal, idCaja);
	}

	@PutMapping("/{idEmpresa}/sucursales/{idSucursal}/cajas/{idCaja}")
	public CajaResponse actualizarCaja(
			@PathVariable Integer idEmpresa,
			@PathVariable Integer idSucursal,
			@PathVariable Integer idCaja,
			@Valid @RequestBody CajaUpdate datos) {
		return cajaService.actualizar(idEmpresa, idSucursal, idCaja, datos);
	}

	@PostMapping("/{idEmpresa}/invitar-empleado")
	public InvitacionEnviadaResponse invitarEmpleado(
			@PathVariable Integer idEmpresa,
			@Valid @RequestBody InvitacionEmpleadoCreate datos) {
		return sucursalService.invitarEmpleado(idEmpresa, datos);
	}

	@PostMapping("/{idEmpresa}/invitar-cliente")
	public InvitacionEnviadaResponse invitarCliente(
			@PathVariable Integer idEmpresa,
			@Valid @RequestBody InvitacionClienteCreate datos) {
		return sucursalService.invitarCliente(idEmpresa, datos);
	}

	@GetMapping("/{idEmpresa}/personal")
	public List<PersonalEmpresaAgrupadoResponse> personal(@PathVariable Integer idEmpresa) {
		return sucursalService.obtenerPersonal(idEmpresa);
	}

	@PutMapping("/{idEmpresa}/editarpersonal")
	public List<PersonalEmpresaResponse> editarPersonal(
			@PathVariable Integer idEmpresa,
			@Valid @RequestBody EditarPersonalCreate datos) {
		return sucursalService.editarPersonal(idEmpresa, datos);
	}

	@GetMapping("/{idEmpresa}/clientes")
	public List<ClienteEmpresaResponse> clientes(@PathVariable Integer idEmpresa) {
		return sucursalService.obtenerClientes(idEmpresa);
	}
}
