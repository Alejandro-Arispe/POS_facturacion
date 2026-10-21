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

    public EmailService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${app.mail-from:}") String mailFrom) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
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
