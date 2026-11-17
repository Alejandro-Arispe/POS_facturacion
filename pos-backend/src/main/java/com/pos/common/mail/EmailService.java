package com.pos.common.mail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String frontendBaseUrl;
    private final boolean enabled;

    /** Resultado de un envío: los errores permanentes (credenciales, dirección inválida) no se reintentan. */
    public enum Resultado { ENVIADO, DESACTIVADO, ERROR_TEMPORAL, ERROR_PERMANENTE }

    public EmailService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${app.mail-from:}") String mailFrom,
            @Value("${app.frontend-base-url:http://localhost:4200}") String frontendBaseUrl,
            @Value("${app.mail.enabled:false}") boolean enabled) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.frontendBaseUrl = frontendBaseUrl;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled && mailSender != null;
    }

    /** Formato válido: dirección RFC 822 estricta y dominio con al menos un punto. */
    public static boolean correoValido(String email) {
        if (email == null || email.isBlank()) return false;
        try {
            InternetAddress address = new InternetAddress(email.trim(), true);
            address.validate();
            String a = address.getAddress();
            int at = a.lastIndexOf('@');
            return at > 0 && a.indexOf('.', at) > at + 1 && !a.endsWith(".");
        } catch (AddressException e) {
            return false;
        }
    }

    public boolean sendVerificationEmail(String email, String nombre, String codigo) {
        String html = """
                <html>
                  <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;">
                    <div style="background-color: white; padding: 20px; border-radius: 8px; max-width: 600px; margin: 0 auto;">
                      <h2>Bienvenido, %s!</h2>
                      <p>Para verificar tu correo electronico y activar tu cuenta, usa el siguiente codigo:</p>
                      <div style="background-color: #f0f0f0; padding: 15px; text-align: center;">
                        <h1 style="letter-spacing: 5px;">%s</h1>
                      </div>
                      <p>Este codigo expira en 15 minutos.</p>
                    </div>
                  </body>
                </html>
                """.formatted(nombre, codigo);
        return send(email, "Codigo de verificacion - POS System", html);
    }

    public boolean sendPasswordRecoveryEmail(String email, String nombre, String nuevaContrasena) {
        String html = """
                <html>
                  <body style="font-family: Arial, sans-serif; padding: 20px;">
                    <h2>Hola, %s</h2>
                    <p>Tu nueva contrasena temporal es:</p>
                    <h1>%s</h1>
                    <p>Cambia esta contrasena despues de iniciar sesion.</p>
                  </body>
                </html>
                """.formatted(nombre, nuevaContrasena);
        return send(email, "Recuperacion de contrasena - POS System", html);
    }

    public boolean sendHtml(String email, String subject, String html) {
        return send(email, subject, html);
    }

    /** Envía un correo multipart (HTML + texto plano) con un PDF adjunto. */
    public Resultado sendWithAttachment(String email, String subject, String html, String texto, String fileName, byte[] pdf) {
        if (!isEnabled()) {
            System.out.println("[MAIL] Envío desactivado (app.mail.enabled=false o SMTP sin configurar); omitido: " + subject);
            return Resultado.DESACTIVADO;
        }
        if (!correoValido(email)) {
            System.out.println("[MAIL] Dirección inválida; no se envía: " + subject);
            return Resultado.ERROR_PERMANENTE;
        }
        try {
            mailSender.send(buildWithAttachment(email, subject, html, texto, fileName, pdf));
            return Resultado.ENVIADO;
        } catch (Exception ex) {
            boolean permanente = esPermanente(ex);
            System.out.println("[MAIL] Error " + (permanente ? "permanente" : "temporal") + " enviando correo: " + ex.getMessage());
            return permanente ? Resultado.ERROR_PERMANENTE : Resultado.ERROR_TEMPORAL;
        }
    }

    /** Arma el mensaje sin enviarlo (multipart/mixed con alternativa texto/HTML y el PDF). */
    public MimeMessage buildWithAttachment(String email, String subject, String html, String texto, String fileName, byte[] pdf)
            throws jakarta.mail.MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
        helper.setTo(email.trim());
        helper.setSubject(subject);
        if (mailFrom != null && !mailFrom.isBlank()) {
            helper.setFrom(mailFrom);
        }
        helper.setText(texto, html);
        helper.addAttachment(fileName, new org.springframework.core.io.ByteArrayResource(pdf), "application/pdf");
        return message;
    }

    /** Credenciales rechazadas o dirección rechazada/mal formada: reintentar no cambia el resultado. */
    static boolean esPermanente(Throwable ex) {
        java.util.Deque<Throwable> pendientes = new java.util.ArrayDeque<>();
        java.util.Set<Throwable> vistos = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        pendientes.add(ex);
        while (!pendientes.isEmpty()) {
            Throwable t = pendientes.poll();
            if (t == null || !vistos.add(t)) continue;
            if (t instanceof org.springframework.mail.MailAuthenticationException
                    || t instanceof org.springframework.mail.MailParseException
                    || t instanceof AuthenticationFailedException
                    || t instanceof AddressException) return true;
            if (t instanceof SendFailedException sf && sf.getInvalidAddresses() != null && sf.getInvalidAddresses().length > 0)
                return true;
            if (t instanceof org.springframework.mail.MailSendException ms)
                pendientes.addAll(ms.getFailedMessages().values().stream()
                        .filter(Throwable.class::isInstance).map(Throwable.class::cast).toList());
            if (t instanceof jakarta.mail.MessagingException me && me.getNextException() != null)
                pendientes.add(me.getNextException());
            if (t.getCause() != null) pendientes.add(t.getCause());
        }
        return false;
    }

    public boolean sendWelcomeEmail(String email, String nombre) {
        String safeName = org.springframework.web.util.HtmlUtils.htmlEscape(nombre == null ? "" : nombre);
        String safeEmail = org.springframework.web.util.HtmlUtils.htmlEscape(email);
        String login = org.springframework.web.util.HtmlUtils.htmlEscape(frontendBaseUrl.replaceAll("/+$", "") + "/login");
        return send(email, "Tu cuenta POS está lista", """
                <html><body style="font-family:Arial,sans-serif;color:#283342;padding:24px">
                <h2>Bienvenido, %s</h2><p>Tu correo ha sido verificado.</p>
                <p><strong>Tu usuario:</strong> %s</p>
                <p>Ingresa con la contraseña que elegiste durante el registro.</p>
                <p><a href="%s">Ingresar a mi cuenta POS</a></p>
                <p>Puedes administrar tu cuenta y tus empresas desde el sistema.
                El plan gratuito no requiere tarjeta; los demás usan Stripe de prueba.</p>
                </body></html>
                """.formatted(safeName, safeEmail, login));
    }

    private boolean send(String to, String subject, String html) {
        if (!isEnabled()) {
            System.out.println("[MAIL] Envío desactivado (app.mail.enabled=false o SMTP sin configurar); omitido: " + subject);
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            if (mailFrom != null && !mailFrom.isBlank()) {
                helper.setFrom(mailFrom);
            }
            helper.setText(html, true);
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            System.out.println("Error sending email: " + ex.getMessage());
            return false;
        }
    }
}
