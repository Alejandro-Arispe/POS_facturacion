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
import com.pos.usuarios.service.RolService;

@RestController
@RequestMapping("/api/roles")
public class RolController {

    private final tools.jackson.databind.ObjectMapper mapper;
    private final RolService rolService;

    public RolController(RolService rolService, tools.jackson.databind.ObjectMapper mapper) {
        this.mapper=mapper;
        this.rolService = rolService;
    }

    @GetMapping("/empresa/{idEmpresa}/mis-campos")
    public Map<String, String> misCampos(@PathVariable Integer idEmpresa) {
        throw retired();
    }

    @GetMapping("/empresa/{idEmpresa}")
    public List<RolResponse> listarPorEmpresa(@PathVariable Integer idEmpresa) {
        return rolService.listarPorEmpresa(idEmpresa);
    }

    @GetMapping("/empresa/{idEmpresa}/capacidades")
    public Map<String,Boolean> capacidades(@PathVariable Integer idEmpresa) { return rolService.capacidades(idEmpresa); }

    @GetMapping("/empresa/{idEmpresa}/delegables")
    public List<Integer> delegables(@PathVariable Integer idEmpresa) { return rolService.delegables(idEmpresa); }

    @GetMapping("/empresa/{idEmpresa}/asignables")
    public List<RolResponse> asignables(@PathVariable Integer idEmpresa) { return rolService.asignables(idEmpresa); }

    @GetMapping("/{idRol}/campos")
    public List<CampoEstadoResponse> listarCampos(
            @PathVariable Integer idRol,
            @RequestParam(name = "formulario", defaultValue = "producto") String formulario) {
        throw retired();
    }

    @PutMapping("/{idRol}/campos")
    public List<CampoEstadoResponse> guardarCampos(
            @PathVariable Integer idRol,
            @RequestBody PrivilegioCampoGuardarRequest body) {
        throw retired();
    }

    @GetMapping("/{idRol}")
    public RolDetalleResponse obtener(@PathVariable Integer idRol) {
        return rolService.obtenerPorId(idRol);
    }

    @PutMapping("/{idRol}")
    public RolDetalleResponse editar(@PathVariable Integer idRol, @RequestBody tools.jackson.databind.JsonNode body) {
        return rolService.editar(idRol, decode(body,RolUpdateRequest.class));
    }

    @PostMapping("/empresa/{idEmpresa}")
    @ResponseStatus(HttpStatus.CREATED)
    public RolCreateResponse crear(@PathVariable Integer idEmpresa, @RequestBody tools.jackson.databind.JsonNode body) {
        return rolService.crear(idEmpresa, decode(body,RolCreateRequest.class));
    }
    private com.pos.common.exception.ApiException retired() {
        return new com.pos.common.exception.ApiException(HttpStatus.GONE, "Formato retirado. Usa mis-permisos y permiso_ids del catálogo canónico.");
    }
    private <T> T decode(tools.jackson.databind.JsonNode body,Class<T> type) {
        if(body==null || !body.isObject()) throw com.pos.common.exception.ApiException.badRequest("Se requiere un objeto de rol.");
        var allowed=java.util.Set.of("nombre","descripcion","activo","permiso_ids","delegable_ids");
        for(var property:body.properties()) {
            if(!allowed.contains(property.getKey()) || property.getValue().isNull())
                throw com.pos.common.exception.ApiException.badRequest("Atributo desconocido o nulo: "+property.getKey());
        }
        return mapper.convertValue(body,type);
    }

}
