package com.pos.siat.service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pos.empresas.domain.Empresa;
import com.pos.ventas.domain.DetalleVenta;
import com.pos.ventas.domain.Factura;

/** HU-18: representación gráfica en PDF de la factura (datos, detalle, CUF y QR de verificación). */
@Service
public class FacturaPdfService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Map<Integer, String> METODOS = Map.of(1, "EFECTIVO", 2, "TARJETA", 7, "TRANSFERENCIA / QR");
    private static final Font TITULO = new Font(Font.HELVETICA, 14, Font.BOLD);
    private static final Font NEGRITA = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final Font NORMAL = new Font(Font.HELVETICA, 9);
    private static final Font PEQUENA = new Font(Font.HELVETICA, 7);

    public byte[] generar(Factura f, Empresa empresa, Integer sucursal, List<DetalleVenta> items) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.LETTER, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph titulo = new Paragraph("FACTURA", TITULO);
            titulo.setAlignment(Element.ALIGN_CENTER);
            doc.add(titulo);
            Paragraph sub = new Paragraph("(Con Derecho a Crédito Fiscal) - Computarizada en Línea", NORMAL);
            sub.setAlignment(Element.ALIGN_CENTER);
            doc.add(sub);
            doc.add(new Paragraph(" "));

            PdfPTable cabecera = new PdfPTable(new float[] {3, 2});
            cabecera.setWidthPercentage(100);
            PdfPTable datos = new PdfPTable(new float[] {1.3f, 2.7f});
            fila(datos, "Emisor", texto(empresa.getRazonSocial(), empresa.getNombre()));
            fila(datos, "NIT", f.getNitEmisor());
            fila(datos, "Sucursal", String.valueOf(sucursal));
            fila(datos, "Factura N°", String.valueOf(f.getNumeroFactura()));
            fila(datos, "Fecha", f.getFechaEmision() == null ? "" : f.getFechaEmision().format(FECHA));
            fila(datos, "Cliente", f.getRazonSocialCliente());
            fila(datos, "NIT/CI/CEX", f.getNitCliente());
            fila(datos, "Método de pago", METODOS.getOrDefault(f.getCodigoMetodoPago(), String.valueOf(f.getCodigoMetodoPago())));
            PdfPCell celdaDatos = new PdfPCell(datos);
            celdaDatos.setBorder(PdfPCell.NO_BORDER);
            cabecera.addCell(celdaDatos);
            PdfPCell celdaQr = f.getQrCodeData() == null ? new PdfPCell(new Phrase("")) : new PdfPCell(qr(f.getQrCodeData()), true);
            celdaQr.setBorder(PdfPCell.NO_BORDER);
            celdaQr.setHorizontalAlignment(Element.ALIGN_CENTER);
            cabecera.addCell(celdaQr);
            doc.add(cabecera);

            Paragraph cuf = new Paragraph("CUF: " + f.getCuf(), PEQUENA);
            doc.add(cuf);
            doc.add(new Paragraph(" "));

            PdfPTable detalle = new PdfPTable(new float[] {1.2f, 4, 1, 1.4f, 1.3f, 1.5f});
            detalle.setWidthPercentage(100);
            for (String h : List.of("Cód. SIN", "Descripción", "Cant.", "P. unit.", "Desc.", "Subtotal")) {
                PdfPCell c = new PdfPCell(new Phrase(h, NEGRITA));
                c.setBackgroundColor(new Color(235, 238, 242));
                detalle.addCell(c);
            }
            for (DetalleVenta d : items) {
                detalle.addCell(new Phrase(String.valueOf(d.getCodigoProductoSin()), NORMAL));
                detalle.addCell(new Phrase(texto(d.getDescripcion(), "#" + d.getIdProducto()), NORMAL));
                detalle.addCell(numero(String.valueOf(d.getCantidad())));
                detalle.addCell(numero(dinero(d.getPrecioUnitario())));
                detalle.addCell(numero(dinero(d.getDescuento())));
                detalle.addCell(numero(dinero(d.getTotal())));
            }
            doc.add(detalle);

            PdfPTable totales = new PdfPTable(new float[] {4, 1.5f});
            totales.setWidthPercentage(50);
            totales.setHorizontalAlignment(Element.ALIGN_RIGHT);
            fila(totales, "TOTAL Bs", dinero(f.getMontoTotal()));
            fila(totales, "Importe base crédito fiscal", dinero(f.getMontoTotal()));
            fila(totales, "IVA 13%", dinero(f.getIva()));
            doc.add(totales);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("ESTA FACTURA CONTRIBUYE AL DESARROLLO DEL PAÍS. EL USO ILÍCITO SERÁ SANCIONADO PENALMENTE DE ACUERDO A LEY", PEQUENA));
            doc.add(new Paragraph("Ley N° 453: Tienes derecho a recibir información sobre las características y contenidos de los servicios que utilices.", PEQUENA));
            doc.add(new Paragraph("Representación gráfica de una factura simulada (no válida ante Impuestos Nacionales).", PEQUENA));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF de la factura: " + e.getMessage(), e);
        }
    }

    private static Image qr(String data) throws Exception {
        BitMatrix m = new QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 180, 180, Map.of(EncodeHintType.MARGIN, 1));
        BufferedImage img = new BufferedImage(m.getWidth(), m.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < m.getWidth(); x++)
            for (int y = 0; y < m.getHeight(); y++)
                img.setRGB(x, y, m.get(x, y) ? 0x000000 : 0xFFFFFF);
        Image image = Image.getInstance(img, null);
        image.scaleToFit(120, 120);
        return image;
    }

    private static void fila(PdfPTable t, String etiqueta, String valor) {
        PdfPCell a = new PdfPCell(new Phrase(etiqueta, NEGRITA));
        PdfPCell b = new PdfPCell(new Phrase(valor == null ? "" : valor, NORMAL));
        a.setBorder(PdfPCell.NO_BORDER);
        b.setBorder(PdfPCell.NO_BORDER);
        t.addCell(a);
        t.addCell(b);
    }

    private static PdfPCell numero(String valor) {
        PdfPCell c = new PdfPCell(new Phrase(valor, NORMAL));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private static String dinero(BigDecimal v) {
        return v == null ? "0.00" : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String texto(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }
}
