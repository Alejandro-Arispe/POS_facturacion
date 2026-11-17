package com.pos.siat.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.clientes.domain.Cliente;
import com.pos.common.access.DataReadPolicy;
import com.pos.common.access.EmpresaAccess;
import com.pos.common.access.ProductFields;
import com.pos.common.exception.ApiException;
import com.pos.empresas.domain.CajaSesion;
import com.pos.empresas.domain.Empresa;
import com.pos.empresas.repo.CajaSesionRepository;
import com.pos.productos.repo.ProductoRepository;
import com.pos.siat.domain.SinMotivoAnulacion;
import com.pos.siat.repo.SinMotivoAnulacionRepository;
import com.pos.usuarios.domain.Usuario;
import com.pos.usuarios.repo.UsuarioRepository;
import com.pos.ventas.domain.DetalleVenta;
import com.pos.ventas.domain.Factura;
import com.pos.ventas.domain.Venta;
import com.pos.ventas.repo.DetalleVentaRepository;
import com.pos.ventas.repo.FacturaRepository;

/**
 * Simulación de la facturación electrónica en línea del SIN (SIAT).
 * No se conecta a Impuestos Nacionales: genera CUIS, CUFD y CUF con un formato coherente,
 * arma el XML de la factura y la URL de verificación para el código QR.
 */
@Service
public class SiatFacturaService {

    /** Estado heredado de facturas anteriores al SIN simulado. */
    public static final String EMITIDA = "EMITIDA";
    public static final String PENDIENTE = "PENDIENTE";
    public static final String VALIDADA = "VALIDADA";
    public static final String OBSERVADA = "OBSERVADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String ANULADA = "ANULADA";
    public static final String CONTINGENCIA = "CONTINGENCIA";

    /** Códigos SIN de tipo de documento de identidad admitidos. */
    private static final Set<Integer> TIPOS_DOCUMENTO = Set.of(1, 2, 3, 4, 5);
    private static final String QR_BASE = "https://siat.impuestos.gob.bo/consulta/QR";
    private static final BigDecimal TASA_IVA = new BigDecimal("0.13");
    private static final DateTimeFormatter CUF_FECHA = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final FacturaRepository facturas;
    private final DetalleVentaRepository detalles;
    private final ProductoRepository productos;
    private final UsuarioRepository usuarios;
    private final CajaSesionRepository sesiones;
    private final SinMotivoAnulacionRepository motivos;
    private final EmpresaAccess empresaAccess;
    private final DataReadPolicy policy;
    private final com.pos.siat.repo.SiatPuntoVentaRepository puntosVenta;
    private final com.pos.siat.sin.SinService sin;
    private final FacturaPdfService pdf;
    private final org.springframework.context.ApplicationEventPublisher eventos;
    private final com.pos.notifications.service.NotificationService notificaciones;

    /** Evento publicado cuando una factura queda VALIDADA: el correo se envía tras confirmar la transacción. */
    public record FacturaValidada(Integer idFactura) {}

    public SiatFacturaService(FacturaRepository facturas, DetalleVentaRepository detalles, ProductoRepository productos,
            UsuarioRepository usuarios, CajaSesionRepository sesiones, SinMotivoAnulacionRepository motivos,
            EmpresaAccess empresaAccess, DataReadPolicy policy,
            com.pos.siat.repo.SiatPuntoVentaRepository puntosVenta, com.pos.siat.sin.SinService sin,
            FacturaPdfService pdf, org.springframework.context.ApplicationEventPublisher eventos,
            com.pos.notifications.service.NotificationService notificaciones) {
        this.puntosVenta = puntosVenta;
        this.sin = sin;
        this.pdf = pdf;
        this.eventos = eventos;
        this.notificaciones = notificaciones;
        this.facturas = facturas;
        this.detalles = detalles;
        this.productos = productos;
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.motivos = motivos;
        this.empresaAccess = empresaAccess;
        this.policy = policy;
    }

    /** Datos del comprador que el POS envía cuando se emite factura. */
    public record Comprador(Integer codigoTipoDocumento, String numeroDocumento, String razonSocial) {}

    /** Emite la factura simulada de una venta ya registrada. Se llama dentro de la transacción de la venta. */
    @Transactional
    public Factura emitir(Venta venta, CajaSesion sesion, Cliente cliente, Comprador comprador, String metodoPagoNombre) {
        return emitir(venta, sesion, cliente, comprador, metodoPagoNombre, true);
    }

    /** enviarCorreo=false para datos de demostración: la factura se emite pero no se envía por correo. */
    @Transactional
    public Factura emitir(Venta venta, CajaSesion sesion, Cliente cliente, Comprador comprador, String metodoPagoNombre,
            boolean enviarCorreo) {
        Empresa empresa = sesion.getCaja().getSucursal().getEmpresa();
        Integer idSucursal = sesion.getCaja().getSucursal().getIdSucursal();
        // HU-17: validación previa. Si algo falla no se envía nada al SIN y el PDV recibe un mensaje por campo.
        Map<String, String> errores = new LinkedHashMap<>();
        String nitEmisor = soloDigitos(empresa.getNit());
        boolean nitValido = nitEmisor.length() >= 7 && nitEmisor.length() <= 13
                && empresa.getNit().trim().matches("[0-9 .-]+");
        if (!nitValido)
            errores.put("nit_emisor", "El NIT de la empresa debe tener entre 7 y 13 dígitos (registrado: '"
                    + (empresa.getNit() == null ? "" : empresa.getNit().trim()) + "').");

        int tipoDocumento = comprador != null && comprador.codigoTipoDocumento() != null ? comprador.codigoTipoDocumento() : 1;
        if (!TIPOS_DOCUMENTO.contains(tipoDocumento))
            errores.put("codigo_tipo_documento", "Tipo de documento de identidad no válido.");
        String documento = comprador == null ? null : limpio(comprador.numeroDocumento());
        String razonSocial = comprador == null ? null : limpio(comprador.razonSocial());
        if (cliente != null && (documento == null || razonSocial == null)) {
            var persona = usuarios.findById(cliente.getIdUsuario()).map(Usuario::getPersona).orElse(null);
            if (persona != null) {
                if (documento == null) documento = limpio(persona.getDocumento());
                if (razonSocial == null) razonSocial = limpio(persona.getNombreCompleto());
            }
        }
        if (documento == null) documento = "0";
        if (razonSocial == null) razonSocial = "S/N";

        // 3) Código de producto SIN obligatorio en cada ítem.
        List<DetalleVenta> items = detalles.findByVenta_IdVenta(venta.getIdVenta());
        for (DetalleVenta d : items) {
            if (d.getCodigoProductoSin() == null || d.getCodigoProductoSin() <= 0)
                errores.put("detalles." + d.getIdProducto() + ".codigo_producto_sin",
                        "El producto '" + (d.getDescripcion() == null ? "#" + d.getIdProducto() : d.getDescripcion())
                                + "' no tiene código de producto SIN.");
        }

        // 2) IVA 13 %: se recalcula desde los ítems y se compara con el total de la venta (tolerancia 0.01).
        BigDecimal tolerancia = new BigDecimal("0.01");
        BigDecimal sumaItems = items.stream().map(d -> nvl(d.getTotal())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal descuentoItems = items.stream().map(d -> nvl(d.getDescuento())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRecalculado = sumaItems.subtract(nvl(venta.getDescuentoTotal()).subtract(descuentoItems));
        BigDecimal total = nvl(venta.getTotal());
        BigDecimal iva = total.multiply(TASA_IVA).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ivaRecalculado = totalRecalculado.multiply(TASA_IVA).setScale(2, RoundingMode.HALF_UP);
        if (totalRecalculado.subtract(total).abs().compareTo(tolerancia) > 0)
            errores.put("monto_total", "El total de la venta (Bs " + total + ") no coincide con la suma de los ítems (Bs "
                    + totalRecalculado.setScale(2, RoundingMode.HALF_UP) + ").");
        if (ivaRecalculado.subtract(iva).abs().compareTo(tolerancia) > 0)
            errores.put("iva", "El IVA 13% (Bs " + iva + ") no coincide con el recalculado desde los ítems (Bs " + ivaRecalculado + ").");

        LocalDateTime ahora = LocalDateTime.now();
        Integer idPuntoVenta = sesion.getCaja().getIdCaja();
        // 1) Reemplaza al "certificado" (no aplica en Computarizada en Línea): CUIS y CUFD vigentes del punto de venta.
        com.pos.siat.domain.SiatPuntoVenta codigos = null;
        if (nitValido) {
            try {
                codigos = codigosVigentes(nitEmisor, idSucursal, idPuntoVenta, ahora);
            } catch (RuntimeException e) {
                errores.put("cuis", "No se pudo obtener el CUIS/CUFD del SIN: " + e.getMessage());
            }
            if (codigos != null && (codigos.getCuis() == null || codigos.getCuis().isBlank()))
                errores.put("cuis", "La empresa no tiene CUIS vigente para este punto de venta.");
            if (codigos != null && (codigos.getCufd() == null || codigos.getCufdVence() == null || !ahora.isBefore(codigos.getCufdVence())))
                errores.put("cufd", "La empresa no tiene CUFD vigente para este punto de venta.");
        }
        if (!errores.isEmpty())
            throw new com.pos.common.exception.FieldValidationException(
                    "La factura no pasó la validación previa y no se envió al SIN.", errores);
        int numero = facturas.maxNumeroPorEmisor(nitEmisor) + 1;
        String cuis = codigos.getCuis();
        String cufd = codigos.getCufd();
        String cuf = cuf(nitEmisor, ahora, idSucursal, idPuntoVenta, numero, codigos.getCufdCodigoControl());

        Factura factura = new Factura();
        factura.setVenta(venta);
        factura.setNitEmisor(nitEmisor);
        factura.setNumeroFactura(numero);
        factura.setFechaEmision(ahora);
        factura.setNitCliente(documento);
        factura.setRazonSocialCliente(razonSocial);
        factura.setCodigoTipoDocumento(tipoDocumento);
        factura.setCodigoMetodoPago(codigoMetodoPago(metodoPagoNombre));
        factura.setMontoTotal(venta.getTotal());
        factura.setIva(iva);
        factura.setCuis(cuis);
        factura.setCufd(cufd);
        factura.setCuf(cuf);
        factura.setEstadoSiat(PENDIENTE);
        factura.setIdPuntoVenta(idPuntoVenta);
        factura.setQrCodeData(QR_BASE + "?nit=" + nitEmisor + "&cuf=" + cuf + "&numero=" + numero + "&t=2");
        firmarXml(factura, xml(factura, empresa, idSucursal, items));
        // HU-18: representación gráfica en PDF (QR, CUF, datos y detalle), guardada en Base64.
        factura.setPdfGenerado(java.util.Base64.getEncoder().encodeToString(pdf.generar(factura, empresa, idSucursal, items)));
        factura = facturas.save(factura);
        enviarAlSin(factura);
        if (!enviarCorreo) {
            factura.setCorreoEstado("OMITIDO");
            factura.setCorreoError("Envío de correo desactivado para esta emisión.");
        }
        postProceso(factura, empresa.getIdEmpresa(), enviarCorreo);
        return facturas.save(factura);
    }

    /** Agrega el hash SHA-256 del XML (Computarizada en Línea: hash, sin firma digital). */
    private static void firmarXml(Factura factura, String cuerpo) {
        String hash = sha256(cuerpo);
        factura.setHashXml(hash);
        factura.setXmlGenerado(cuerpo.replace("</facturaComputarizadaCompraVenta>\n",
                "  <hashXml algoritmo=\"SHA-256\">" + hash + "</hashXml>\n</facturaComputarizadaCompraVenta>\n"));
    }

    /** Envía al SIN; si no responde, la factura queda en CONTINGENCIA para reenviarse sola. */
    private void enviarAlSin(Factura factura) {
        try {
            aplicar(factura, sin.recibirFactura(factura.getCuf(), factura.getXmlGenerado(), factura.getHashXml()));
        } catch (RuntimeException e) {
            factura.setEstadoSiat(CONTINGENCIA);
            factura.setMensajeSin("SIN no disponible: " + e.getMessage());
        }
    }

    /** Reenvía al SIN una factura en contingencia, rechazada u observada (XML y hash regenerados). */
    @Transactional
    public Factura reenviarAlSin(Factura factura, boolean publicarValidada) {
        var sucursal = sesionDe(factura).getCaja().getSucursal();
        firmarXml(factura, xml(factura, sucursal.getEmpresa(), sucursal.getIdSucursal(),
                detalles.findByVenta_IdVenta(factura.getVenta().getIdVenta())));
        enviarAlSin(factura);
        postProceso(factura, sucursal.getEmpresa().getIdEmpresa(), publicarValidada);
        return facturas.save(factura);
    }

    /** RECHAZADA/OBSERVADA: avisa al admin en el centro de notificaciones. VALIDADA: programa el correo. */
    private void postProceso(Factura factura, Integer idEmpresa, boolean publicarValidada) {
        String estado = factura.getEstadoSiat();
        if (RECHAZADA.equals(estado) || OBSERVADA.equals(estado)) {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("tipo", "FACTURA_SIN");
                payload.put("id_factura", factura.getIdFactura());
                payload.put("id_venta", factura.getVenta().getIdVenta());
                payload.put("estado_siat", estado);
                notificaciones.enviarAlerta(idEmpresa, "Factura N° " + factura.getNumeroFactura() + " " + estado + " por el SIN",
                        (factura.getMensajeSin() == null ? "" : factura.getMensajeSin() + " ")
                                + "Revísala y reenvíala desde Reportes > Facturas.", payload);
            } catch (RuntimeException e) {
                System.out.println("[FACTURA] No se pudo notificar el rechazo: " + e.getMessage());
            }
        }
        if (VALIDADA.equals(estado) && publicarValidada && factura.getCorreoEstado() == null) {
            factura.setCorreoEstado("PENDIENTE");
            eventos.publishEvent(new FacturaValidada(factura.getIdFactura()));
        }
    }

    /** PDF de la factura; las facturas anteriores sin PDF lo generan y guardan al pedirlo. */
    @Transactional
    public byte[] pdfDe(Factura factura) {
        if (factura.getPdfGenerado() != null && !factura.getPdfGenerado().isBlank())
            return java.util.Base64.getDecoder().decode(factura.getPdfGenerado());
        var sucursal = sesionDe(factura).getCaja().getSucursal();
        byte[] bytes = pdf.generar(factura, sucursal.getEmpresa(), sucursal.getIdSucursal(),
                detalles.findByVenta_IdVenta(factura.getVenta().getIdVenta()));
        factura.setPdfGenerado(java.util.Base64.getEncoder().encodeToString(bytes));
        facturas.save(factura);
        return bytes;
    }

    /** Factura de la empresa indicada, con permiso de reimpresión/reenvío en su sucursal. */
    @Transactional(readOnly = true)
    public Factura requireFacturaEmpresa(Usuario usuario, Integer idEmpresa, Integer idFactura) {
        Factura factura = facturas.findById(idFactura).orElseThrow(() -> ApiException.notFound("Factura no encontrada."));
        var sucursal = sesionDe(factura).getCaja().getSucursal();
        if (!sucursal.getEmpresa().getIdEmpresa().equals(idEmpresa)) throw ApiException.notFound("Factura no encontrada.");
        empresaAccess.requirePermiso(usuario, idEmpresa, sucursal.getIdSucursal(), "invoice:reprint");
        return factura;
    }

    private static <T> T operacionSin(java.util.function.Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (com.pos.siat.sin.SinNoDisponibleException e) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "El SIN no responde en este momento. Intenta más tarde.");
        }
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** CUIS (una vez por punto de venta) y CUFD vigente; renueva el CUFD si venció (24 h). */
    private com.pos.siat.domain.SiatPuntoVenta codigosVigentes(String nit, Integer sucursal, Integer puntoVenta, LocalDateTime ahora) {
        com.pos.siat.domain.SiatPuntoVenta pv = puntosVenta.findByNitEmisorAndIdSucursalAndIdPuntoVenta(nit, sucursal, puntoVenta)
                .orElseGet(() -> {
                    com.pos.siat.domain.SiatPuntoVenta nuevo = new com.pos.siat.domain.SiatPuntoVenta();
                    nuevo.setNitEmisor(nit);
                    nuevo.setIdSucursal(sucursal);
                    nuevo.setIdPuntoVenta(puntoVenta);
                    nuevo.setCuis(sin.solicitarCuis(nit, sucursal, puntoVenta));
                    nuevo.setFechaCuis(ahora);
                    return nuevo;
                });
        if (pv.getCufd() == null || pv.getCufdVence() == null || !ahora.isBefore(pv.getCufdVence())) {
            com.pos.siat.sin.SinService.Cufd cufd = sin.solicitarCufd(nit, sucursal, puntoVenta, pv.getCuis());
            pv.setCufd(cufd.codigo());
            pv.setCufdCodigoControl(cufd.codigoControl());
            pv.setCufdObtenido(cufd.obtenido());
            pv.setCufdVence(cufd.vence());
        }
        return puntosVenta.save(pv);
    }

    private static void aplicar(Factura factura, com.pos.siat.sin.SinService.Respuesta r) {
        if (Set.of(VALIDADA, OBSERVADA, RECHAZADA, ANULADA).contains(r.estado())) factura.setEstadoSiat(r.estado());
        if (r.codigoRecepcion() != null) factura.setCodigoRecepcion(r.codigoRecepcion());
        factura.setMensajeSin(r.mensaje());
    }

    /** Consulta el estado de la factura en el SIN simulado y sincroniza el estado local. */
    @Transactional
    public Map<String, Object> consultarEstado(Usuario usuario, Integer idFactura) {
        Factura factura = facturas.findById(idFactura).orElseThrow(() -> ApiException.notFound("Factura no encontrada."));
        var sucursal = sesionDe(factura).getCaja().getSucursal();
        policy.require(usuario, sucursal.getEmpresa().getIdEmpresa(), sucursal.getIdSucursal(), "invoice:reprint", "sale:read");
        com.pos.siat.sin.SinService.Respuesta r = operacionSin(() -> sin.consultarEstado(factura.getCuf()));
        if (!"NO_ENCONTRADA".equals(r.estado())) {
            aplicar(factura, r);
            facturas.save(factura);
        }
        Map<String, Object> result = resumen(factura);
        result.put("estado_sin", r.estado());
        result.put("codigo_recepcion", factura.getCodigoRecepcion());
        result.put("mensaje_sin", r.mensaje());
        return result;
    }

    /** Detalle completo de una factura para su representación gráfica. */
    @Transactional(readOnly = true)
    public Map<String, Object> detalle(Usuario usuario, Integer idFactura) {
        Factura factura = facturas.findById(idFactura).orElseThrow(() -> ApiException.notFound("Factura no encontrada."));
        CajaSesion sesion = sesionDe(factura);
        var sucursal = sesion.getCaja().getSucursal();
        var grants = policy.require(usuario, sucursal.getEmpresa().getIdEmpresa(), sucursal.getIdSucursal(), "invoice:reprint", "sale:read");
        policy.productFields(grants, ProductFields.ALL.toArray(String[]::new));

        Map<String, Object> result = resumen(factura);
        result.put("cuis", factura.getCuis());
        result.put("cufd", factura.getCufd());
        result.put("hash_xml", factura.getHashXml());
        result.put("codigo_recepcion", factura.getCodigoRecepcion());
        result.put("mensaje_sin", factura.getMensajeSin());
        result.put("qr_code_data", factura.getQrCodeData());
        result.put("codigo_tipo_documento", factura.getCodigoTipoDocumento());
        result.put("codigo_metodo_pago", factura.getCodigoMetodoPago());
        result.put("iva", factura.getIva());
        result.put("codigo_motivo_anulacion", factura.getCodigoMotivoAnulacion());
        result.put("fecha_anulacion", factura.getFechaAnulacion());
        result.put("razon_social_emisor", sucursal.getEmpresa().getRazonSocial());
        result.put("nombre_empresa", sucursal.getEmpresa().getNombre());
        result.put("sucursal", sucursal.getNombre());
        result.put("direccion_sucursal", sucursal.getDireccion());
        result.put("ciudad_sucursal", sucursal.getCiudad());
        result.put("telefono_sucursal", sucursal.getTelefono());
        List<Map<String, Object>> items = new ArrayList<>();
        for (DetalleVenta d : detalles.findByVenta_IdVenta(factura.getVenta().getIdVenta())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("cantidad", d.getCantidad());
            item.put("descripcion", productos.findById(d.getIdProducto()).map(p -> p.getNombre()).orElse(d.getDescripcion()));
            item.put("precio_unitario", d.getPrecioUnitario());
            item.put("descuento", d.getDescuento());
            item.put("subtotal", d.getTotal());
            item.put("codigo_producto_sin", d.getCodigoProductoSin());
            item.put("codigo_unidad_medida_sin", d.getCodigoUnidadMedidaSin());
            items.add(item);
        }
        result.put("detalles", items);
        return result;
    }

    /** Anula una factura emitida registrando el motivo SIN. */
    @Transactional
    public Map<String, Object> anular(Usuario usuario, Integer idFactura, Integer codigoMotivo) {
        Factura factura = facturas.findById(idFactura).orElseThrow(() -> ApiException.notFound("Factura no encontrada."));
        var sucursal = sesionDe(factura).getCaja().getSucursal();
        empresaAccess.requirePermiso(usuario, sucursal.getEmpresa().getIdEmpresa(), sucursal.getIdSucursal(), "sale:void");
        if (codigoMotivo == null) throw ApiException.badRequest("Selecciona el motivo de anulación.");
        SinMotivoAnulacion motivo = motivos.findById(codigoMotivo)
                .orElseThrow(() -> ApiException.badRequest("Motivo de anulación no válido."));
        if (ANULADA.equals(factura.getEstadoSiat())) throw ApiException.badRequest("La factura ya se encuentra anulada.");
        // Una factura en contingencia o pendiente nunca llegó al SIN: se anula solo localmente.
        if (!CONTINGENCIA.equals(factura.getEstadoSiat()) && !PENDIENTE.equals(factura.getEstadoSiat())) {
            com.pos.siat.sin.SinService.Respuesta r = operacionSin(() -> sin.anular(factura.getCuf(), motivo.getCodigoMotivoSin()));
            if (!ANULADA.equals(r.estado())) throw ApiException.badRequest("El SIN no anuló la factura: " + r.mensaje());
            factura.setMensajeSin(r.mensaje());
        }
        factura.setEstadoSiat(ANULADA);
        factura.setCodigoMotivoAnulacion(motivo.getCodigoMotivoSin());
        factura.setFechaAnulacion(LocalDateTime.now());
        facturas.save(factura);
        Map<String, Object> result = resumen(factura);
        result.put("mensaje", "Factura anulada: " + motivo.getDescripcion() + ".");
        return result;
    }

    /** Campos comunes del listado y del detalle. */
    public Map<String, Object> resumen(Factura factura) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id_factura", factura.getIdFactura());
        row.put("id_venta", factura.getVenta().getIdVenta());
        row.put("numero_factura", factura.getNumeroFactura());
        row.put("fecha_emision", factura.getFechaEmision());
        row.put("nit_emisor", factura.getNitEmisor());
        row.put("nit_cliente", factura.getNitCliente());
        row.put("nombre_cliente", factura.getRazonSocialCliente());
        row.put("monto_total", factura.getMontoTotal());
        row.put("cuf", factura.getCuf());
        row.put("estado_siat", factura.getEstadoSiat() == null ? EMITIDA : factura.getEstadoSiat());
        return row;
    }

    /** Nombre comercial (o razón social) de la empresa emisora, para el correo de la factura. */
    @Transactional(readOnly = true)
    public String nombreEmpresa(Factura factura) {
        Empresa empresa = sesionDe(factura).getCaja().getSucursal().getEmpresa();
        return empresa.getNombre() != null && !empresa.getNombre().isBlank() ? empresa.getNombre() : empresa.getRazonSocial();
    }

    private CajaSesion sesionDe(Factura factura) {
        return sesiones.findWithCajaSucursalEmpresa(factura.getVenta().getIdCajaSesion())
                .orElseThrow(() -> ApiException.notFound("Factura no encontrada."));
    }

    /**
     * CUF simulado siguiendo la estructura del SIN: NIT + fecha/hora + sucursal + modalidad (2, computarizada)
     * + tipo de emisión (1, en línea) + tipo de factura (1, con crédito fiscal) + documento sector (1, compra-venta)
     * + número de factura + punto de venta, más un dígito módulo 11. Se convierte a hexadecimal y se le agrega
     * el código de control que el SIN entrega junto con el CUFD.
     */
    private static String cuf(String nit, LocalDateTime fecha, Integer sucursal, Integer puntoVenta, int numero, String codigoControl) {
        String base = pad(nit, 13) + fecha.format(CUF_FECHA) + pad(String.valueOf(sucursal), 4)
                + "2" + "1" + "1" + pad("1", 2) + pad(String.valueOf(numero), 10) + pad(String.valueOf(puntoVenta), 4);
        String conVerificador = base + modulo11(base);
        return new BigInteger(conVerificador).toString(16).toUpperCase(Locale.ROOT) + codigoControl;
    }

    private static int modulo11(String digits) {
        int sum = 0;
        int factor = 2;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sum += (digits.charAt(i) - '0') * factor;
            factor = factor == 9 ? 2 : factor + 1;
        }
        int dv = 11 - (sum % 11);
        return dv == 11 ? 0 : dv == 10 ? 1 : dv;
    }

    /** Código SIN del método de pago a partir del nombre registrado en el sistema. */
    private static int codigoMetodoPago(String nombre) {
        String n = nombre == null ? "" : nombre.trim().toUpperCase(Locale.ROOT);
        return switch (n) {
            case "TARJETA" -> 2;
            case "QR", "TRANSFERENCIA" -> 7;
            default -> 1;
        };
    }

    private static String xml(Factura f, Empresa empresa, Integer sucursal, List<DetalleVenta> items) {
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<facturaComputarizadaCompraVenta>\n  <cabecera>\n");
        tag(x, "nitEmisor", f.getNitEmisor());
        tag(x, "razonSocialEmisor", empresa.getRazonSocial());
        tag(x, "numeroFactura", f.getNumeroFactura());
        tag(x, "cuf", f.getCuf());
        tag(x, "cufd", f.getCufd());
        tag(x, "codigoSucursal", sucursal);
        tag(x, "fechaEmision", f.getFechaEmision());
        tag(x, "nombreRazonSocial", f.getRazonSocialCliente());
        tag(x, "codigoTipoDocumentoIdentidad", f.getCodigoTipoDocumento());
        tag(x, "numeroDocumento", f.getNitCliente());
        tag(x, "codigoMetodoPago", f.getCodigoMetodoPago());
        tag(x, "montoTotal", f.getMontoTotal());
        tag(x, "montoTotalSujetoIva", f.getMontoTotal());
        tag(x, "codigoMoneda", 1);
        tag(x, "leyenda", "Ley N° 453: Tienes derecho a recibir información sobre las características y contenidos de los servicios que utilices.");
        tag(x, "codigoDocumentoSector", 1);
        x.append("  </cabecera>\n");
        for (DetalleVenta d : items) {
            x.append("  <detalle>\n");
            tag(x, "codigoProductoSin", d.getCodigoProductoSin());
            tag(x, "codigoProducto", d.getIdProducto());
            tag(x, "descripcion", d.getDescripcion());
            tag(x, "cantidad", d.getCantidad());
            tag(x, "unidadMedida", d.getCodigoUnidadMedidaSin());
            tag(x, "precioUnitario", d.getPrecioUnitario());
            tag(x, "montoDescuento", d.getDescuento());
            tag(x, "subTotal", d.getTotal());
            x.append("  </detalle>\n");
        }
        return x.append("</facturaComputarizadaCompraVenta>\n").toString();
    }

    private static void tag(StringBuilder x, String name, Object value) {
        String text = value == null ? "" : value.toString()
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        x.append("    <").append(name).append('>').append(text).append("</").append(name).append(">\n");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String pad(String value, int length) {
        String digits = soloDigitos(value);
        if (digits.length() > length) digits = digits.substring(digits.length() - length);
        return "0".repeat(length - digits.length()) + digits;
    }

    private static String soloDigitos(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static String limpio(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
