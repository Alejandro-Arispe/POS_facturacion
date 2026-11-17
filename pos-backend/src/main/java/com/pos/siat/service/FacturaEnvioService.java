package com.pos.siat.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.pos.clientes.repo.ClienteRepository;
import com.pos.common.exception.ApiException;
import com.pos.common.mail.EmailService;
import com.pos.siat.sin.SinService;
import com.pos.usuarios.domain.Usuario;
import com.pos.usuarios.repo.UsuarioRepository;
import com.pos.ventas.domain.Factura;
import com.pos.ventas.repo.FacturaRepository;

/**
 * HU-18 y contingencia: envío del PDF por correo con hasta 3 intentos, reenvío automático al SIN de las
 * facturas en contingencia cuando vuelve a responder, y reenvío manual desde el historial.
 */
@Service
public class FacturaEnvioService {

    public static final int MAX_INTENTOS_CORREO = 3;
    public static final String CORREO_PENDIENTE = "PENDIENTE";
    public static final String CORREO_ENVIADO = "ENVIADO";
    public static final String CORREO_OMITIDO = "OMITIDO";
    public static final String CORREO_FALLIDO = "FALLIDO";

    private final FacturaRepository facturas;
    private final SiatFacturaService siat;
    private final EmailService email;
    private final ClienteRepository clientes;
    private final UsuarioRepository usuarios;
    private final SinService sin;
    private final TransactionTemplate tx;
    private final long reintentoMs;

    public FacturaEnvioService(FacturaRepository facturas, SiatFacturaService siat, EmailService email,
            ClienteRepository clientes, UsuarioRepository usuarios, SinService sin, PlatformTransactionManager txManager,
            @org.springframework.beans.factory.annotation.Value("${app.facturas.reintento-ms:60000}") long reintentoMs) {
        this.facturas = facturas;
        this.siat = siat;
        this.email = email;
        this.clientes = clientes;
        this.usuarios = usuarios;
        this.sin = sin;
        this.tx = new TransactionTemplate(txManager);
        this.reintentoMs = reintentoMs;
    }

    /** Primer intento de correo, una vez confirmada la venta y en otro hilo: el SMTP nunca demora la venta. */
    @org.springframework.scheduling.annotation.Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alValidar(SiatFacturaService.FacturaValidada evento) {
        try {
            enviarCorreo(evento.idFactura());
        } catch (RuntimeException e) {
            System.out.println("[FACTURA] Envío de correo diferido al reintento: " + e.getMessage());
        }
    }

    /** Cola simple: reenvía al SIN las facturas en contingencia y reintenta los correos pendientes. */
    @Scheduled(fixedDelayString = "${app.facturas.reintento-ms:60000}", initialDelayString = "${app.facturas.reintento-ms:60000}")
    public void procesarPendientes() {
        if (sin.disponible()) {
            for (Integer id : facturas.findIdsByEstadoSiat(SiatFacturaService.CONTINGENCIA)) {
                try {
                    tx.executeWithoutResult(s -> facturas.findById(id).ifPresent(f -> siat.reenviarAlSin(f, true)));
                } catch (RuntimeException e) {
                    System.out.println("[FACTURA] Contingencia " + id + " sigue pendiente: " + e.getMessage());
                }
            }
        }
        LocalDateTime limite = LocalDateTime.now().minusNanos(reintentoMs * 1_000_000L);
        for (Integer id : facturas.findIdsCorreoPendiente(MAX_INTENTOS_CORREO, limite)) {
            try {
                enviarCorreo(id);
            } catch (RuntimeException e) {
                System.out.println("[FACTURA] Correo de la factura " + id + " falló: " + e.getMessage());
            }
        }
    }

    /** Un intento de envío del PDF; registra intento, fecha, destino y resultado. Devuelve el correo usado. */
    public String enviarCorreo(Integer idFactura) {
        return tx.execute(s -> {
            Factura f = facturas.findById(idFactura).orElse(null);
            if (f == null || !SiatFacturaService.VALIDADA.equals(f.getEstadoSiat())) return null;
            String correo = correoCliente(f);
            if (correo == null) {
                f.setCorreoEstado(CORREO_OMITIDO);
                f.setCorreoError("El cliente no tiene correo registrado.");
                return null;
            }
            if (!email.isEnabled()) {
                f.setCorreoEstado(CORREO_OMITIDO);
                f.setCorreoError("Envío de correo desactivado (app.mail.enabled=false).");
                return null;
            }
            int intento = (f.getCorreoIntentos() == null ? 0 : f.getCorreoIntentos()) + 1;
            f.setCorreoDestino(correo);
            f.setCorreoIntentos(intento);
            f.setCorreoUltimoIntento(LocalDateTime.now());
            if (!EmailService.correoValido(correo)) {
                f.setCorreoEstado(CORREO_FALLIDO);
                f.setCorreoError("El correo del cliente no tiene un formato válido.");
                return correo;
            }
            EmailService.Resultado resultado;
            String error = null;
            try {
                FacturaCorreo c = FacturaCorreo.de(f, siat.nombreEmpresa(f));
                resultado = email.sendWithAttachment(correo, c.asunto(), c.html(), c.texto(), c.adjunto(), siat.pdfDe(f));
            } catch (RuntimeException e) {
                resultado = EmailService.Resultado.ERROR_TEMPORAL;
                error = e.getMessage();
            }
            switch (resultado) {
                case ENVIADO -> {
                    f.setCorreoEstado(CORREO_ENVIADO);
                    f.setCorreoError(null);
                }
                case DESACTIVADO -> {
                    f.setCorreoEstado(CORREO_OMITIDO);
                    f.setCorreoError("Envío de correo desactivado (app.mail.enabled=false).");
                }
                case ERROR_PERMANENTE -> {
                    f.setCorreoEstado(CORREO_FALLIDO);
                    f.setCorreoError("Error permanente (credenciales SMTP o dirección rechazada); no se reintenta.");
                }
                case ERROR_TEMPORAL -> {
                    f.setCorreoEstado(intento >= MAX_INTENTOS_CORREO ? CORREO_FALLIDO : CORREO_PENDIENTE);
                    f.setCorreoError(error != null ? error : "No se pudo enviar el correo (intento " + intento + " de " + MAX_INTENTOS_CORREO + ").");
                }
            }
            return correo;
        });
    }

    /** Reenvío manual: reenvía al SIN si no está validada y, si queda validada, reenvía el correo con 3 intentos nuevos. */
    public Map<String, Object> reenviar(Usuario usuario, Integer idEmpresa, Integer idFactura) {
        if (idFactura == null) throw ApiException.badRequest("Indica la factura a reenviar.");
        Factura factura = siat.requireFacturaEmpresa(usuario, idEmpresa, idFactura);
        if (SiatFacturaService.ANULADA.equals(factura.getEstadoSiat()))
            throw ApiException.badRequest("Una factura anulada no se puede reenviar.");
        boolean reenvioSin = !SiatFacturaService.VALIDADA.equals(factura.getEstadoSiat())
                && !SiatFacturaService.EMITIDA.equals(factura.getEstadoSiat());
        if (reenvioSin)
            tx.executeWithoutResult(s -> facturas.findById(idFactura).ifPresent(f -> siat.reenviarAlSin(f, false)));
        String correo = null;
        boolean validada = Boolean.TRUE.equals(tx.execute(s -> facturas.findById(idFactura).map(f -> {
            if (!SiatFacturaService.VALIDADA.equals(f.getEstadoSiat())) return false;
            f.setCorreoIntentos(0);
            f.setCorreoEstado(CORREO_PENDIENTE);
            return true;
        }).orElse(false)));
        if (validada) correo = enviarCorreo(idFactura);
        Factura f = facturas.findById(idFactura).orElseThrow();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id_factura", f.getIdFactura());
        result.put("estado_siat", f.getEstadoSiat());
        result.put("correo_estado", f.getCorreoEstado());
        result.put("correo_intentos", f.getCorreoIntentos());
        result.put("correo_cliente", correo);
        result.put("mensaje", mensaje(f, reenvioSin));
        return result;
    }

    private String mensaje(Factura f, boolean reenvioSin) {
        String sinTexto = reenvioSin ? "Factura reenviada al SIN: " + f.getEstadoSiat() + ". " : "";
        if (!SiatFacturaService.VALIDADA.equals(f.getEstadoSiat()))
            return sinTexto + (f.getMensajeSin() == null ? "" : f.getMensajeSin());
        return sinTexto + switch (String.valueOf(f.getCorreoEstado())) {
            case CORREO_ENVIADO -> "Correo enviado al cliente.";
            case CORREO_OMITIDO -> f.getCorreoError() != null ? "No se envió: " + f.getCorreoError() : "El cliente no tiene correo; no se envió.";
            case CORREO_FALLIDO -> "No se pudo enviar el correo: " + f.getCorreoError();
            default -> "No se pudo enviar el correo; se reintentará automáticamente.";
        };
    }

    private String correoCliente(Factura f) {
        Integer idCliente = f.getVenta().getIdCliente();
        if (idCliente == null) return null;
        return clientes.findById(idCliente)
                .flatMap(c -> usuarios.findById(c.getIdUsuario()))
                .map(Usuario::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .filter(e -> !e.trim().toLowerCase().endsWith("@example.com")) // datos demo: sin correo real
                .orElse(null);
    }
}
