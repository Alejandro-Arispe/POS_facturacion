package com.pos.usuarios.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pos.common.security.SecurityUtils;
import com.pos.usuarios.dto.CampoEstadoResponse;
import com.pos.usuarios.dto.PrivilegioCampoGuardarRequest;
import com.pos.usuarios.dto.RolCreateRequest;
import com.pos.usuarios.dto.RolCreateResponse;
import com.pos.usuarios.dto.RolDetalleResponse;
import com.pos.usuarios.dto.RolResponse;
import com.pos.usuarios.dto.RolUpdateRequest;
import com.pos.usuarios.service.PrivilegioCampoService;
import com.pos.usuarios.service.RolService;

@RestController
@RequestMapping("/api/roles")
public class RolController {

    private final RolService rolService;
    private final PrivilegioCampoService privilegioCampoService;

    public RolController(RolService rolService, PrivilegioCampoService privilegioCampoService) {
        this.rolService = rolService;
        this.privilegioCampoService = privilegioCampoService;
    }

    @GetMapping("/empresa/{idEmpresa}/mis-campos")
    public Map<String, String> misCampos(@PathVariable Integer idEmpresa) {
        return privilegioCampoService.misCamposProducto(SecurityUtils.currentUser(), idEmpresa);
    }

    @GetMapping("/empresa/{idEmpresa}")
    public List<RolResponse> listarPorEmpresa(@PathVariable Integer idEmpresa) {
        return rolService.listarPorEmpresa(idEmpresa);
    }

    @GetMapping("/{idRol}/campos")
    public List<CampoEstadoResponse> listarCampos(
            @PathVariable Integer idRol,
            @RequestParam(name = "formulario", defaultValue = "producto") String formulario) {
        return privilegioCampoService.listar(SecurityUtils.currentUser(), idRol, formulario);
    }

    @PutMapping("/{idRol}/campos")
    public List<CampoEstadoResponse> guardarCampos(
            @PathVariable Integer idRol,
            @RequestBody PrivilegioCampoGuardarRequest body) {
        return privilegioCampoService.guardar(SecurityUtils.currentUser(), idRol, body);
    }

    @GetMapping("/{idRol}")
    public RolDetalleResponse obtener(@PathVariable Integer idRol) {
        return rolService.obtenerPorId(idRol);
    }

    @PutMapping("/{idRol}")
    public RolDetalleResponse editar(@PathVariable Integer idRol, @RequestBody RolUpdateRequest body) {
        return rolService.editar(idRol, body);
    }

    @PostMapping("/empresa/{idEmpresa}")
    @ResponseStatus(HttpStatus.CREATED)
    public RolCreateResponse crear(@PathVariable Integer idEmpresa, @RequestBody RolCreateRequest body) {
        return rolService.crear(idEmpresa, body);
    }
}
