package com.pos.common.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.pos.siat.service.FacturaCorreo;
import com.pos.ventas.domain.Factura;

import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

/** Correo de factura: contenido, multipart y adjunto. Nunca envía nada: el sender cuenta y bloquea los envíos. */
class FacturaCorreoTests {

    /** Sender sin servidor: cualquier intento de envío real se cuenta y se rechaza. */
    static class SinEnvio extends JavaMailSenderImpl {
        final AtomicInteger envios = new AtomicInteger();

        @Override
        protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
            envios.incrementAndGet();
            throw new AssertionError("No se debe enviar correo en los tests");
        }
    }

    private static Factura factura() {
        Factura f = new Factura();
        f.setNumeroFactura(42);
        f.setFechaEmision(LocalDateTime.of(2026, 11, 17, 10, 30));
        f.setRazonSocialCliente("Ana <Pérez>");
        f.setNitCliente("1234567");
        f.setMontoTotal(new BigDecimal("113.00"));
        f.setIva(new BigDecimal("14.69"));
        f.setCuf("ABC123DEF456");
        f.setQrCodeData("https://siat.example/consulta?nit=1&cuf=ABC123DEF456&numero=42&t=2");
        return f;
    }

    @Test
    void contenidoDelCorreo() {
        FacturaCorreo c = FacturaCorreo.de(factura(), "Minimarket Demo");
        assertEquals("Factura N° 42 - Minimarket Demo", c.asunto());
        assertEquals("Factura-42.pdf", c.adjunto());
        String html = c.html();
        assertTrue(html.contains("Minimarket Demo"));
        assertTrue(html.contains("<table"));
        for (String s : List.of("N° factura", "17/11/2026 10:30", "Ana &lt;Pérez&gt;", "NIT/CI", "1234567",
                "Subtotal", "98.31", "IVA 13%", "14.69", "Total Bs", "113.00", "monospace", "ABC123DEF456",
                "Verificar factura", "href=\"https://siat.example/consulta?nit=1&amp;cuf=ABC123DEF456&amp;numero=42&amp;t=2\"",
                "mensaje automático"))
            assertTrue(html.contains(s), "Falta en el HTML: " + s);
        assertFalse(html.contains("<Pérez>"), "El nombre del cliente debe ir escapado");
        assertTrue(c.texto().contains("Total Bs: 113.00"));
        assertTrue(c.texto().contains("Verificar factura: https://siat.example/consulta"));
        assertFalse(c.texto().contains("<html") || c.texto().contains("<table") || c.texto().contains("<a "));
        assertTrue(c.texto().contains("Cliente: Ana <Pérez>"));
    }

    @Test
    void mensajeMultipartConAdjuntoSinEnviar() throws Exception {
        SinEnvio sender = new SinEnvio();
        EmailService service = new EmailService(sender, "pos@example.com", "http://localhost:4200", true);
        FacturaCorreo c = FacturaCorreo.de(factura(), "Minimarket Demo");
        byte[] pdf = "%PDF-1.4 prueba".getBytes();

        MimeMessage m = service.buildWithAttachment("cliente@example.com", c.asunto(), c.html(), c.texto(), c.adjunto(), pdf);
        m.saveChanges();

        assertEquals("Factura N° 42 - Minimarket Demo", m.getSubject());
        assertEquals("cliente@example.com", ((InternetAddress) m.getAllRecipients()[0]).getAddress());
        Map<String, List<Part>> partes = new java.util.HashMap<>();
        recorrer(m, partes);
        Part adjunto = partes.getOrDefault("application/pdf", List.of()).get(0);
        assertEquals("Factura-42.pdf", adjunto.getFileName());
        assertTrue(Part.ATTACHMENT.equalsIgnoreCase(adjunto.getDisposition()));
        assertEquals(pdf.length, adjunto.getInputStream().readAllBytes().length);
        assertNotNull(partes.get("text/plain"), "Falta la versión de texto plano");
        assertTrue(((String) partes.get("text/html").get(0).getContent()).contains("Verificar factura"));
        assertTrue(((String) partes.get("text/plain").get(0).getContent()).contains("Total Bs: 113.00"));
        assertEquals(0, sender.envios.get());
    }

    @Test
    void desactivadoNoEnvia() {
        SinEnvio sender = new SinEnvio();
        EmailService service = new EmailService(sender, "", "http://localhost:4200", false);
        assertFalse(service.isEnabled());
        assertEquals(EmailService.Resultado.DESACTIVADO,
                service.sendWithAttachment("cliente@example.com", "s", "<p>h</p>", "t", "Factura-1.pdf", new byte[] {1}));
        assertFalse(service.sendHtml("cliente@example.com", "s", "<p>h</p>"));
        assertEquals(0, sender.envios.get());
    }

    @Test
    void direccionInvalidaEsPermanenteSinEnviar() {
        SinEnvio sender = new SinEnvio();
        EmailService service = new EmailService(sender, "", "http://localhost:4200", true);
        for (String malo : List.of("sin-arroba", "a@b", "a@@b.com", "a b@c.com", " "))
            assertFalse(EmailService.correoValido(malo), malo);
        assertTrue(EmailService.correoValido("cliente@example.com"));
        assertEquals(EmailService.Resultado.ERROR_PERMANENTE,
                service.sendWithAttachment("a@b", "s", "<p>h</p>", "t", "Factura-1.pdf", new byte[] {1}));
        assertEquals(0, sender.envios.get());
    }

    @Test
    void clasificaErrores() {
        assertTrue(EmailService.esPermanente(new MailAuthenticationException("535 auth failed")));
        assertTrue(EmailService.esPermanente(new MailSendException("x", new jakarta.mail.AuthenticationFailedException("535"))));
        assertTrue(EmailService.esPermanente(new MailSendException(Map.of(new Object(),
                new jakarta.mail.SendFailedException("550", null, null, null, new jakarta.mail.Address[] {
                        new InternetAddress() })))));
        assertFalse(EmailService.esPermanente(new MailSendException("Connection timed out",
                new jakarta.mail.MessagingException("timeout"))));
    }

    private static void recorrer(Part p, Map<String, List<Part>> out) throws Exception {
        Object content = p.getContent();
        if (content instanceof MimeMultipart mp) {
            for (int i = 0; i < mp.getCount(); i++) recorrer(mp.getBodyPart(i), out);
        } else {
            String tipo = p.getContentType().toLowerCase().split(";")[0].trim();
            out.computeIfAbsent(tipo, k -> new ArrayList<>()).add(p);
        }
    }
}
