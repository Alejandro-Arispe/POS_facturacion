package com.pos.respaldos;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pos.common.exception.ApiException;
import com.pos.common.security.SecurityUtils;

@RestController
@RequestMapping("/api/empresas/{idEmpresa}/respaldos")
public class RespaldoController {

    private final RespaldoService respaldoService;

    public RespaldoController(RespaldoService respaldoService) {
        this.respaldoService = respaldoService;
    }

    /**
     * Respaldo manual: acepta payload opcional { "id_sucursal": 1 } o sin cuerpo (toda la empresa).
     */
    @PostMapping
    public RespaldoRespuestas.Generado crear(
            @PathVariable Integer idEmpresa,
            @RequestBody(required = false) RespaldoRespuestas.CrearManualDTO dto) {
        Integer idSucursal = dto != null ? dto.idSucursal() : null;
        return respaldoService.crear(idEmpresa, idSucursal, SecurityUtils.currentUser());
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

    /**
     * Restaura a partir de un archivo previamente almacenado en el servidor.
     */
    @PostMapping("/{archivo:.+}/restaurar")
    public RespaldoRespuestas.Restaurado restaurar(
            @PathVariable Integer idEmpresa,
            @PathVariable String archivo) {
        return respaldoService.restaurar(idEmpresa, SecurityUtils.currentUser(), archivo);
    }

    /**
     * Restaura a partir de un archivo JSON subido directamente por el usuario (pendrive / almacenamiento local).
     */
    @PostMapping(value = "/restaurar-archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RespaldoRespuestas.Restaurado restaurarArchivo(
            @PathVariable Integer idEmpresa,
            @RequestParam("archivo") MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw ApiException.badRequest("Debe seleccionar un archivo de respaldo válido.");
        }
        try {
            byte[] bytes = archivo.getBytes();
            return respaldoService.restaurarDesdeBytes(
                    idEmpresa,
                    SecurityUtils.currentUser(),
                    bytes,
                    archivo.getOriginalFilename());
        } catch (IOException ex) {
            throw ApiException.badRequest("No se pudo leer el archivo de respaldo.");
        }
    }

    /**
     * Consulta la configuración de respaldo automático y capacidades del plan de la empresa.
     */
    @GetMapping("/configuracion")
    public RespaldoRespuestas.ConfiguracionDTO obtenerConfiguracion(@PathVariable Integer idEmpresa) {
        return respaldoService.obtenerConfiguracion(idEmpresa, SecurityUtils.currentUser());
    }

    /**
     * Actualiza la configuración de respaldo automático.
     */
    @PutMapping("/configuracion")
    public RespaldoRespuestas.ConfiguracionDTO guardarConfiguracion(
            @PathVariable Integer idEmpresa,
            @RequestBody RespaldoRespuestas.GuardarConfiguracionDTO dto) {
        return respaldoService.guardarConfiguracion(idEmpresa, dto, SecurityUtils.currentUser());
    }
}
