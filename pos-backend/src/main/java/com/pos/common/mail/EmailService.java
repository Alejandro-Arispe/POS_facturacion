package com.pos.common.mail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String frontendBaseUrl;

    public EmailService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${app.mail-from:}") String mailFrom,
            @Value("${app.frontend-base-url:http://localhost:4200}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.frontendBaseUrl = frontendBaseUrl;
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
        if (mailSender == null) {
            System.out.println("SMTP no configurado; email omitido: " + subject);
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
