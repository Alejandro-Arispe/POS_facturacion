package com.pos.empresas.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pos.usuarios.dto.PermisosPorModuloResponse;
import com.pos.usuarios.service.ModuloPermisoService;

@RestController
@RequestMapping("/api/empresas")
public class EmpresaPermisoController {

    private final ModuloPermisoService moduloPermisoService;

    public EmpresaPermisoController(ModuloPermisoService moduloPermisoService) {
        this.moduloPermisoService = moduloPermisoService;
    }

    @GetMapping("/permisos-por-modulo")
    public List<PermisosPorModuloResponse> listarPermisosPorModulo() {
        return moduloPermisoService.listarPermisosPorModulo();
    }
}
