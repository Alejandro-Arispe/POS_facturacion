package com.pos.siat.web;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.pos.common.security.SecurityUtils;
import com.pos.siat.domain.SinMotivoAnulacion;
import com.pos.siat.domain.SinProductoServicio;
import com.pos.siat.domain.SinUnidadMedida;
import com.pos.siat.repo.SinMotivoAnulacionRepository;
import com.pos.siat.repo.SinProductoServicioRepository;
import com.pos.siat.repo.SinUnidadMedidaRepository;
import com.pos.siat.service.SiatFacturaService;

/** Catálogos paramétricos del SIN y operaciones de factura de la simulación SIAT. */
@RestController
public class SiatController {

    private final SinProductoServicioRepository productos;
    private final SinUnidadMedidaRepository unidades;
    private final SinMotivoAnulacionRepository motivos;
    private final SiatFacturaService facturas;
    @org.springframework.beans.factory.annotation.Autowired private com.pos.siat.service.FacturaEnvioService envio;
    @org.springframework.beans.factory.annotation.Autowired private com.pos.siat.sin.SinService sin;

    public SiatController(SinProductoServicioRepository productos, SinUnidadMedidaRepository unidades,
            SinMotivoAnulacionRepository motivos, SiatFacturaService facturas) {
        this.productos = productos;
        this.unidades = unidades;
        this.motivos = motivos;
        this.facturas = facturas;
    }

    @GetMapping("/api/sin/productos-servicios")
    public List<SinProductoServicio> productosServicios() {
        return productos.findAll(Sort.by("codigoActividad", "codigoProductoSin"));
    }

    @GetMapping("/api/sin/unidades-medida")
    public List<SinUnidadMedida> unidadesMedida() {
        return unidades.findAll(Sort.by("codigoUnidadSin"));
    }

    @GetMapping("/api/sin/motivos-anulacion")
    public List<SinMotivoAnulacion> motivosAnulacion() {
        return motivos.findAll(Sort.by("codigoMotivoSin"));
    }

    @org.springframework.beans.factory.annotation.Autowired private com.pos.ventas.service.InvoiceReadService invoiceRead;

    /** HU-16: facturas del cliente autenticado. */
    @GetMapping("/api/facturas/me")
    public List<Map<String, Object>> misFacturas() {
        return invoiceRead.mine();
    }

    /** HU-16/18: PDF de una factura propia del cliente autenticado. */
    @GetMapping("/api/facturas/me/{idFactura}/pdf")
    public org.springframework.http.ResponseEntity<byte[]> miFacturaPdf(@PathVariable Integer idFactura) {
        var pdf = invoiceRead.minePdf(idFactura);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"" + pdf.nombre() + "\"")
                .body(pdf.bytes());
    }

    @GetMapping("/api/facturas/{idFactura}")
    public Map<String, Object> detalle(@PathVariable Integer idFactura) {
        return facturas.detalle(SecurityUtils.currentUser(), idFactura);
    }

    /** HU-18: reenvío manual desde el historial (al SIN si hace falta y el PDF al correo del cliente). */
    @PostMapping("/api/empresas/{idEmpresa}/facturas/reenviar")
    public Map<String, Object> reenviar(@PathVariable Integer idEmpresa, @RequestBody Map<String, Integer> body) {
        return envio.reenviar(SecurityUtils.currentUser(), idEmpresa, body == null ? null : body.get("id_factura"));
    }

    /** Bandera para simular la caída del SIN (solo superadministrador). */
    @org.springframework.web.bind.annotation.GetMapping("/api/sin/simulado/disponibilidad")
    public Map<String, Object> disponibilidad() {
        requireSuperAdmin();
        return Map.of("disponible", sin.disponible());
    }

    @org.springframework.web.bind.annotation.PutMapping("/api/sin/simulado/disponibilidad")
    public Map<String, Object> cambiarDisponibilidad(@RequestBody Map<String, Boolean> body) {
        requireSuperAdmin();
        if (body == null || body.get("disponible") == null)
            throw com.pos.common.exception.ApiException.badRequest("Indica 'disponible': true o false.");
        sin.setDisponible(body.get("disponible"));
        return Map.of("disponible", sin.disponible());
    }

    private static void requireSuperAdmin() {
        var usuario = SecurityUtils.currentUser();
        if (usuario == null || !usuario.esSuperAdmin())
            throw com.pos.common.exception.ApiException.forbidden("Solo el superadministrador puede simular la caída del SIN.");
    }

    /** Estado de la factura consultado al SIN simulado. */
    @GetMapping("/api/facturas/{idFactura}/estado")
    public Map<String, Object> estado(@PathVariable Integer idFactura) {
        return facturas.consultarEstado(SecurityUtils.currentUser(), idFactura);
    }

    @PostMapping("/api/facturas/{idFactura}/anular")
    public Map<String, Object> anular(@PathVariable Integer idFactura, @RequestBody Map<String, Integer> body) {
        return facturas.anular(SecurityUtils.currentUser(), idFactura, body == null ? null : body.get("codigo_motivo_anulacion"));
    }
}
