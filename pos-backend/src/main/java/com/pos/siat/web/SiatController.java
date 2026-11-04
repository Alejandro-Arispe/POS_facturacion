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

    @GetMapping("/api/facturas/{idFactura}")
    public Map<String, Object> detalle(@PathVariable Integer idFactura) {
        return facturas.detalle(SecurityUtils.currentUser(), idFactura);
    }

    @PostMapping("/api/facturas/{idFactura}/anular")
    public Map<String, Object> anular(@PathVariable Integer idFactura, @RequestBody Map<String, Integer> body) {
        return facturas.anular(SecurityUtils.currentUser(), idFactura, body == null ? null : body.get("codigo_motivo_anulacion"));
    }
}
