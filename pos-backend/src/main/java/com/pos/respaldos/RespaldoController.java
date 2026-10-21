package com.pos.respaldos;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pos.common.security.SecurityUtils;

@RestController
@RequestMapping("/api/empresas/{idEmpresa}/respaldos")
public class RespaldoController {

    private final RespaldoService respaldoService;

    public RespaldoController(RespaldoService respaldoService) {
        this.respaldoService = respaldoService;
    }

    @PostMapping
    public RespaldoRespuestas.Generado crear(@PathVariable Integer idEmpresa) {
        return respaldoService.crear(idEmpresa, SecurityUtils.currentUser());
    }

    @GetMapping
    public List<RespaldoRespuestas.Resumen> listar(@PathVariable Integer idEmpresa) {
        return respaldoService.listar(idEmpresa, SecurityUtils.currentUser());
    }

    @GetMapping("/{archivo:.+}")
    public ResponseEntity<byte[]> descargar(
            @PathVariable Integer idEmpresa,
            @PathVariable String archivo) {
        byte[] cuerpo = respaldoService.descargar(idEmpresa, SecurityUtils.currentUser(), archivo);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo);
    }

    @PostMapping("/{archivo:.+}/restaurar")
    public RespaldoRespuestas.Restaurado restaurar(
            @PathVariable Integer idEmpresa,
            @PathVariable String archivo) {
        return respaldoService.restaurar(idEmpresa, SecurityUtils.currentUser(), archivo);
    }
}
