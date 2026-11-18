package com.pos.siat.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

import org.springframework.web.util.HtmlUtils;

import com.pos.ventas.domain.Factura;

/** HU-18: contenido del correo de la factura (asunto, HTML con estilos en línea, texto plano y nombre del PDF). */
public record FacturaCorreo(String asunto, String html, String texto, String adjunto) {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static FacturaCorreo de(Factura f, String empresa) {
        String emp = empresa == null || empresa.isBlank() ? "POS" : empresa.trim();
        String numero = String.valueOf(f.getNumeroFactura());
        String fecha = f.getFechaEmision() == null ? "" : f.getFechaEmision().format(FECHA);
        String cliente = f.getRazonSocialCliente() == null ? "" : f.getRazonSocialCliente();
        String nit = f.getNitCliente() == null ? "" : f.getNitCliente();
        BigDecimal total = f.getMontoTotal() == null ? BigDecimal.ZERO : f.getMontoTotal();
        BigDecimal iva = f.getIva() == null ? BigDecimal.ZERO : f.getIva();
        String cuf = f.getCuf() == null ? "" : f.getCuf();
        String url = f.getQrCodeData() == null ? "" : f.getQrCodeData();

        String asunto = "Factura N° " + numero + " - " + emp;
        String adjunto = "Factura-" + numero + ".pdf";

        String td = "padding:8px 12px;border-bottom:1px solid #e3e7ee;";
        String th = td + "color:#5b6576;text-align:left;font-weight:normal;width:45%;";
        StringBuilder filas = new StringBuilder();
        String[][] datos = {
                {"N° factura", numero}, {"Fecha", fecha}, {"Cliente", cliente}, {"NIT/CI", nit},
                {"Subtotal", "Bs " + dinero(total.subtract(iva))}, {"IVA 13%", "Bs " + dinero(iva)},
        };
        for (String[] d : datos)
            filas.append("<tr><th style=\"").append(th).append("\">").append(e(d[0])).append("</th><td style=\"")
                    .append(td).append("\">").append(e(d[1])).append("</td></tr>");
        filas.append("<tr><th style=\"").append(th).append("font-weight:bold;color:#283342;\">Total Bs</th><td style=\"")
                .append(td).append("font-weight:bold;font-size:16px;\">").append(e(dinero(total))).append("</td></tr>");

        String boton = url.isBlank() ? "" : """
                <p style="text-align:center;margin:24px 0">
                <a href="%s" style="display:inline-block;background:#1f6feb;color:#ffffff;text-decoration:none;padding:12px 24px;border-radius:6px;font-weight:bold">Verificar factura</a>
                </p>""".formatted(e(url));

        String html = """
                <html><body style="margin:0;padding:24px;background:#f4f6f9;font-family:Arial,sans-serif;color:#283342">
                <div style="max-width:600px;margin:0 auto;background:#ffffff;border-radius:8px;overflow:hidden">
                <div style="background:#283342;color:#ffffff;padding:20px 24px">
                <h1 style="margin:0;font-size:20px">%s</h1>
                <p style="margin:4px 0 0;font-size:14px;color:#c9d1dd">Factura N° %s</p>
                </div>
                <div style="padding:24px">
                <p style="margin:0 0 16px">Hola %s, adjuntamos tu factura en PDF.</p>
                <table role="presentation" style="width:100%%;border-collapse:collapse;font-size:14px">%s</table>
                <p style="margin:16px 0 4px;font-size:12px;color:#5b6576">CUF</p>
                <p style="margin:0;font-family:'Courier New',Courier,monospace;font-size:12px;word-break:break-all;background:#f4f6f9;padding:8px;border-radius:4px">%s</p>
                %s
                </div>
                <div style="padding:16px 24px;background:#f4f6f9;font-size:11px;color:#8a93a3;text-align:center">
                Este es un mensaje automático, por favor no respondas a este correo.
                </div>
                </div>
                </body></html>
                """.formatted(e(emp), e(numero), e(cliente), filas, e(cuf), boton);

        String texto = """
                %s
                Factura N° %s

                Hola %s, adjuntamos tu factura en PDF.

                N° factura: %s
                Fecha: %s
                Cliente: %s
                NIT/CI: %s
                Subtotal: Bs %s
                IVA 13%%: Bs %s
                Total Bs: %s

                CUF: %s
                %s
                --
                Este es un mensaje automático, por favor no respondas a este correo.
                """.formatted(emp, numero, cliente, numero, fecha, cliente, nit, dinero(total.subtract(iva)), dinero(iva),
                dinero(total), cuf, url.isBlank() ? "" : "Verificar factura: " + url + "\n");

        return new FacturaCorreo(asunto, html, texto, adjunto);
    }

    private static String dinero(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String e(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s, "UTF-8");
    }
}
