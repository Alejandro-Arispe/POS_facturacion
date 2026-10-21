package com.pos.reportes.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pos.common.security.SecurityUtils;
import com.pos.reportes.dto.ReporteDtos;
import com.pos.reportes.service.ReportesService;

@RestController
@RequestMapping("/api/reportes")
public class ReportesController {

    private final ReportesService reportesService;

    public ReportesController(ReportesService reportesService) {
        this.reportesService = reportesService;
    }

    @GetMapping({"/plantillas", "/templates"})
    public List<ReporteDtos.Plantilla> plantillas() {
        SecurityUtils.currentUser();
        return reportesService.catalogo();
    }

    @GetMapping("/{empresaId}/resumenventas")
    public Map<String, Object> resumenVentas(
            @PathVariable Integer empresaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return reportesService.resumenVentas(SecurityUtils.currentUser(), empresaId, fecha);
    }

    @GetMapping("/{empresaId}/detallesventas")
    public Map<String, Object> detalleVentas(
            @PathVariable Integer empresaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return reportesService.detalleVentas(SecurityUtils.currentUser(), empresaId, fecha);
    }

    @GetMapping("/{empresaId}/estadoinventario")
    public Map<String, Object> estadoInventario(@PathVariable Integer empresaId) {
        return reportesService.estadoInventario(SecurityUtils.currentUser(), empresaId);
    }

    @GetMapping("/{empresaId}/movimientosinventario")
    public Map<String, Object> movimientosInventario(@PathVariable Integer empresaId) {
        return reportesService.movimientosInventario(SecurityUtils.currentUser(), empresaId);
    }

    @GetMapping("/{empresaId}/resumencajas")
    public Map<String, Object> resumenCajas(@PathVariable Integer empresaId) {
        return reportesService.resumenCajas(SecurityUtils.currentUser(), empresaId);
    }

    @GetMapping("/{empresaId}/movimientoscaja")
    public Map<String, Object> movimientosCaja(@PathVariable Integer empresaId) {
        return reportesService.movimientosCaja(SecurityUtils.currentUser(), empresaId);
    }

    @PostMapping({"/{empresaId}/interpretar", "/{empresaId}/interpret"})
    public ReporteDtos.RespuestaInterpretacion interpretar(
            @PathVariable Integer empresaId, @RequestBody ReporteDtos.Solicitud solicitud) {
        SecurityUtils.currentUser();
        return reportesService.interpretar(empresaId, solicitud.getPrompt());
    }

    @PostMapping({"/{empresaId}/ejecutar", "/{empresaId}/run"})
    public ReporteDtos.RespuestaReporte ejecutar(
            @PathVariable Integer empresaId, @RequestBody ReporteDtos.Solicitud solicitud) {
        return reportesService.ejecutar(SecurityUtils.currentUser(), empresaId, solicitud.getPrompt());
    }

    @PostMapping("/productos-abastecimiento")
    public List<ReporteDtos.ProductoAbastecimiento> abastecimiento(
            @RequestBody ReporteDtos.SucursalRequest request) {
        return reportesService.abastecimiento(SecurityUtils.currentUser(), request.getIdSucursal());
    }

    @PostMapping("/recomendar-productos")
    public ReporteDtos.RecomendacionResponse recomendar(
            @RequestBody ReporteDtos.RecomendacionRequest request) {
        return reportesService.recomendar(SecurityUtils.currentUser(), request.getIdProductos());
    }

    @PostMapping("/{empresaId}/ventasparametrizado")
    public Map<String, Object> ventasParam(
            @PathVariable Integer empresaId, @RequestBody ReporteDtos.VentasParamRequest filtros) {
        return reportesService.ventasParametrizado(SecurityUtils.currentUser(), empresaId, filtros);
    }

    @PostMapping("/{empresaId}/inventarioparametrizado")
    public Map<String, Object> inventarioParam(
            @PathVariable Integer empresaId, @RequestBody ReporteDtos.InventarioParamRequest filtros) {
        return reportesService.inventarioParametrizado(SecurityUtils.currentUser(), empresaId, filtros);
    }

    @PostMapping("/{empresaId}/cajasparametrizado")
    public Map<String, Object> cajasParam(
            @PathVariable Integer empresaId, @RequestBody ReporteDtos.CajasParamRequest filtros) {
        return reportesService.cajasParametrizado(SecurityUtils.currentUser(), empresaId, filtros);
    }
}
