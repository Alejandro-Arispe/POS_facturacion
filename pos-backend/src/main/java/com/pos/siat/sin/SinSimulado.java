package com.pos.siat.sin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pos.siat.domain.SinRecepcion;
import com.pos.siat.repo.SinRecepcionRepository;

/**
 * SIN simulado: no se conecta a Impuestos Nacionales. Emite CUIS/CUFD, recibe facturas verificando el
 * hash SHA-256 del XML y guarda su propio registro de recepciones para consultas y anulaciones.
 */
@Service
public class SinSimulado implements SinService {

    private static final Pattern HASH_TAG = Pattern.compile("[ \\t]*<hashXml[^>]*>[^<]*</hashXml>\\r?\\n?");
    private static final Pattern PRODUCTO_SIN_VACIO = Pattern.compile("<codigoProductoSin>\\s*</codigoProductoSin>");

    private final SinRecepcionRepository recepciones;
    private volatile boolean disponible;

    public SinSimulado(SinRecepcionRepository recepciones,
            @org.springframework.beans.factory.annotation.Value("${app.sin-simulado.disponible:true}") boolean disponible) {
        this.recepciones = recepciones;
        this.disponible = disponible;
    }

    @Override
    public boolean disponible() {
        return disponible;
    }

    @Override
    public void setDisponible(boolean disponible) {
        this.disponible = disponible;
    }

    private void requireDisponible() {
        if (!disponible) throw new SinNoDisponibleException();
    }

    @Override
    public String solicitarCuis(String nit, Integer sucursal, Integer puntoVenta) {
        requireDisponible();
        return sha256(nit + "|CUIS|" + sucursal + "|" + puntoVenta + "|" + UUID.randomUUID()).substring(0, 8);
    }

    @Override
    public Cufd solicitarCufd(String nit, Integer sucursal, Integer puntoVenta, String cuis) {
        requireDisponible();
        LocalDateTime ahora = LocalDateTime.now();
        String codigo = sha256(nit + "|CUFD|" + sucursal + "|" + puntoVenta + "|" + cuis + "|" + ahora + "|" + UUID.randomUUID());
        return new Cufd(codigo.substring(0, 40), codigo.substring(40, 55), ahora, ahora.plusHours(24));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Respuesta recibirFactura(String cuf, String xml, String hashXml) {
        requireDisponible();
        if (cuf == null || xml == null || hashXml == null) return new Respuesta("RECHAZADA", null, "Envío incompleto.");
        var previa = recepciones.findById(cuf);
        // Una factura VALIDADA o ANULADA no se recibe de nuevo; RECHAZADA u OBSERVADA se vuelve a evaluar.
        if (previa.isPresent() && !Set.of("RECHAZADA", "OBSERVADA").contains(previa.get().getEstado())) return respuesta(previa.get());
        String estado;
        String mensaje;
        if (!hashXml.equalsIgnoreCase(hashDe(xml))) {
            estado = "RECHAZADA";
            mensaje = "El hash del archivo no coincide con el XML recibido.";
        } else if (PRODUCTO_SIN_VACIO.matcher(xml).find()) {
            estado = "OBSERVADA";
            mensaje = "Hay detalles sin código de producto SIN.";
        } else {
            estado = "VALIDADA";
            mensaje = "Factura recibida y validada.";
        }
        SinRecepcion r = previa.orElseGet(SinRecepcion::new);
        r.setCuf(cuf);
        r.setEstado(estado);
        r.setCodigoRecepcion(UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT));
        r.setFechaRecepcion(LocalDateTime.now());
        r.setMensaje(mensaje);
        return respuesta(recepciones.save(r));
    }

    @Override
    @Transactional(readOnly = true)
    public Respuesta consultarEstado(String cuf) {
        requireDisponible();
        return recepciones.findById(cuf).map(this::respuesta)
                .orElse(new Respuesta("NO_ENCONTRADA", null, "El SIN no tiene registrada esta factura."));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Respuesta anular(String cuf, Integer codigoMotivo) {
        requireDisponible();
        SinRecepcion r = recepciones.findById(cuf).orElse(null);
        if (r == null) return new Respuesta("NO_ENCONTRADA", null, "El SIN no tiene registrada esta factura.");
        if ("RECHAZADA".equals(r.getEstado()))
            return new Respuesta("RECHAZADA", r.getCodigoRecepcion(), "Una factura rechazada no se puede anular.");
        if (!"ANULADA".equals(r.getEstado())) {
            r.setEstado("ANULADA");
            r.setCodigoMotivoAnulacion(codigoMotivo);
            r.setMensaje("Factura anulada.");
            recepciones.save(r);
        }
        return respuesta(r);
    }

    /** Hash SHA-256 del XML sin su propio elemento hashXml (el hash no puede cubrirse a sí mismo). */
    public static String hashDe(String xml) {
        return sha256(HASH_TAG.matcher(xml).replaceAll(""));
    }

    private Respuesta respuesta(SinRecepcion r) {
        return new Respuesta(r.getEstado(), r.getCodigoRecepcion(), r.getMensaje());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
