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

    public static final String EMITIDA = "EMITIDA";
    public static final String ANULADA = "ANULADA";

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

    public SiatFacturaService(FacturaRepository facturas, DetalleVentaRepository detalles, ProductoRepository productos,
            UsuarioRepository usuarios, CajaSesionRepository sesiones, SinMotivoAnulacionRepository motivos,
            EmpresaAccess empresaAccess, DataReadPolicy policy) {
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
        Empresa empresa = sesion.getCaja().getSucursal().getEmpresa();
        Integer idSucursal = sesion.getCaja().getSucursal().getIdSucursal();
        String nitEmisor = soloDigitos(empresa.getNit());
        if (nitEmisor.isEmpty()) throw ApiException.badRequest("La empresa no tiene un NIT válido para emitir facturas.");

        int tipoDocumento = comprador != null && comprador.codigoTipoDocumento() != null ? comprador.codigoTipoDocumento() : 1;
        if (!TIPOS_DOCUMENTO.contains(tipoDocumento)) throw ApiException.badRequest("Tipo de documento de identidad no válido.");
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

        LocalDateTime ahora = LocalDateTime.now();
        int numero = facturas.maxNumeroPorEmisor(nitEmisor) + 1;
        String cuis = cuis(nitEmisor, idSucursal);
        String cufd = cufd(nitEmisor, idSucursal, ahora.toLocalDate());
        String cuf = cuf(nitEmisor, ahora, idSucursal, numero, cufd);

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
        factura.setIva(venta.getTotal().multiply(TASA_IVA).setScale(2, RoundingMode.HALF_UP));
        factura.setCuis(cuis);
        factura.setCufd(cufd);
        factura.setCuf(cuf);
        factura.setEstadoSiat(EMITIDA);
        factura.setQrCodeData(QR_BASE + "?nit=" + nitEmisor + "&cuf=" + cuf + "&numero=" + numero + "&t=2");
        factura.setXmlGenerado(xml(factura, empresa, idSucursal, detalles.findByVenta_IdVenta(venta.getIdVenta())));
        // La representación gráfica se genera en el frontend a partir del detalle de la factura.
        factura.setPdfGenerado("");
        return facturas.save(factura);
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

    private CajaSesion sesionDe(Factura factura) {
        return sesiones.findWithCajaSucursalEmpresa(factura.getVenta().getIdCajaSesion())
                .orElseThrow(() -> ApiException.notFound("Factura no encontrada."));
    }

    /** CUIS simulado: estable por emisor y sucursal (en el SIN se obtiene una vez por punto de venta). */
    private static String cuis(String nit, Integer sucursal) {
        return sha256(nit + "|CUIS|" + sucursal).substring(0, 8);
    }

    /** CUFD simulado: cambia cada día, como el código diario que entrega el SIN. */
    private static String cufd(String nit, Integer sucursal, LocalDate fecha) {
        return sha256(nit + "|CUFD|" + sucursal + "|" + fecha).substring(0, 40);
    }

    /**
     * CUF simulado siguiendo la estructura del SIN: NIT + fecha/hora + sucursal + modalidad (1, electrónica)
     * + tipo de emisión (1, en línea) + tipo de factura (1, con crédito fiscal) + documento sector (1, compra-venta)
     * + número de factura + punto de venta, más un dígito módulo 11. Se convierte a hexadecimal y se le agrega
     * el código de control derivado del CUFD.
     */
    private static String cuf(String nit, LocalDateTime fecha, Integer sucursal, int numero, String cufd) {
        String base = pad(nit, 13) + fecha.format(CUF_FECHA) + pad(String.valueOf(sucursal), 4)
                + "1" + "1" + "1" + pad("1", 2) + pad(String.valueOf(numero), 10) + pad("0", 4);
        String conVerificador = base + modulo11(base);
        return new BigInteger(conVerificador).toString(16).toUpperCase(Locale.ROOT) + cufd.substring(cufd.length() - 15);
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
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<facturaElectronicaCompraVenta>\n  <cabecera>\n");
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
        return x.append("</facturaElectronicaCompraVenta>\n").toString();
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
