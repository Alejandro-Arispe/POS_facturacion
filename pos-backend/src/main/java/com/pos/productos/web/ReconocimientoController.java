package com.pos.productos.web;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pos.common.security.SecurityUtils;
import com.pos.productos.service.ReconocimientoProductoService;

/** HU-27: "Escanear por camara" en el PDV. */
@RestController
public class ReconocimientoController {

    private final ReconocimientoProductoService reconocimiento;

    public ReconocimientoController(ReconocimientoProductoService reconocimiento) {
        this.reconocimiento = reconocimiento;
    }

    @PostMapping("/api/empresas/{idEmpresa}/sucursales/{idSucursal}/productos/reconocer")
    public Map<String, Object> reconocer(@PathVariable Integer idEmpresa, @PathVariable Integer idSucursal,
            @RequestParam("imagen") MultipartFile imagen) {
        return reconocimiento.reconocer(SecurityUtils.currentUser(), idEmpresa, idSucursal, imagen);
    }
}
