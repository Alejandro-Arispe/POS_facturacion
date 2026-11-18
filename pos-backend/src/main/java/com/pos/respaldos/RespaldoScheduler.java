package com.pos.respaldos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.pos.common.mail.EmailService;
import com.pos.empresas.domain.Empresa;
import com.pos.empresas.domain.HistorialSuscripcion;
import com.pos.empresas.repo.HistorialSuscripcionRepository;
import com.pos.empresas.service.PlanPolicy;

@Component
public class RespaldoScheduler {

    private static final Logger log = LoggerFactory.getLogger(RespaldoScheduler.class);

    /** Las horas configuradas son hora de Bolivia, sin importar la zona del servidor (EC2 suele estar en UTC). */
    static final ZoneId ZONA = ZoneId.of("America/La_Paz");

    private final ConfiguracionRespaldoRepository configRepository;
    private final HistorialSuscripcionRepository suscripciones;
    private final RespaldoService respaldoService;
    private final RespaldoAlmacen almacen;
    private final EmailService emailService;

    public RespaldoScheduler(
            ConfiguracionRespaldoRepository configRepository,
            HistorialSuscripcionRepository suscripciones,
            RespaldoService respaldoService,
            RespaldoAlmacen almacen,
            EmailService emailService) {
        this.configRepository = configRepository;
        this.suscripciones = suscripciones;
        this.respaldoService = respaldoService;
        this.almacen = almacen;
        this.emailService = emailService;
    }

    /**
     * Revisa cada minuto si a alguna empresa le toca su respaldo. Respeta horas y minutos, y si el
     * servidor estuvo apagado a la hora programada, lo ejecuta en cuanto vuelve (una vez por dia).
     * Sin @Transactional: cada respaldo corre en su propia transaccion para que un fallo no afecte a los demas.
     */
    @Scheduled(cron = "0 * * * * *", zone = "America/La_Paz")
    public void procesarRespaldosProgramados() {
        LocalDateTime ahora = LocalDateTime.now(ZONA);
        for (ConfiguracionRespaldo config : configRepository.findActivasConEmpresa()) {
            Empresa empresa = config.getEmpresa();
            if (!Boolean.TRUE.equals(empresa.getActivo()) || !tocaEjecutar(config, ahora)) {
                continue;
            }
            Integer idEmpresa = empresa.getIdEmpresa();
            String planCodigo = obtenerCodigoPlan(idEmpresa);
            // El plan pudo bajar despues de configurar el respaldo.
            if (!"basico".equalsIgnoreCase(planCodigo) && !"pro".equalsIgnoreCase(planCodigo)) {
                continue;
            }
            try {
                Integer idSucursal = "SUCURSAL".equalsIgnoreCase(config.getAlcance()) && config.getSucursal() != null
                        ? config.getSucursal().getIdSucursal()
                        : null;

                RespaldoRespuestas.Generado generado = respaldoService.exportarProgramado(idEmpresa, idSucursal);
                if (generado == null) {
                    continue;
                }
                config.setUltimoRespaldoExitoso(ahora);
                configRepository.save(config);
                log.info("Respaldo programado exitoso para empresa {}: {}", idEmpresa, generado.archivo());

                // Envio cloud por email, exclusivo del plan Pro
                if ("pro".equalsIgnoreCase(planCodigo) && Boolean.TRUE.equals(config.getEnviarEmail())
                        && config.getEmailDestino() != null && !config.getEmailDestino().isBlank()) {
                    byte[] archivoBytes = almacen.leer(idEmpresa, generado.archivo());
                    boolean enviado = emailService.sendBackupEmail(
                            config.getEmailDestino(), empresa.getNombre(), generado.archivo(), archivoBytes);
                    if (enviado) {
                        log.info("Respaldo cloud enviado por email a {} para empresa {}", config.getEmailDestino(), idEmpresa);
                    } else {
                        log.warn("No se pudo enviar por email el respaldo de la empresa {}", idEmpresa);
                    }
                }
            } catch (Exception ex) {
                log.error("Error al ejecutar respaldo automatico para empresa {}: {}", idEmpresa, ex.getMessage(), ex);
            }
        }
    }

    static boolean tocaEjecutar(ConfiguracionRespaldo config, LocalDateTime ahora) {
        LocalTime hora = horaProgramada(config.getHora());
        if (hora == null || ahora.toLocalTime().isBefore(hora)) {
            return false;
        }
        LocalDateTime ultimo = config.getUltimoRespaldoExitoso();
        if (ultimo != null && !ultimo.toLocalDate().isBefore(ahora.toLocalDate())) {
            return false; // ya se ejecuto hoy
        }
        String frecuencia = config.getFrecuencia() != null ? config.getFrecuencia().toUpperCase() : "DESACTIVADO";
        return switch (frecuencia) {
            case "DIARIO" -> true;
            case "SEMANAL" -> ahora.getDayOfWeek().getValue() == 7; // Domingo
            case "MENSUAL" -> ahora.getDayOfMonth() == 1; // Primer dia del mes
            default -> false;
        };
    }

    /** Convierte "HH:mm" en LocalTime; null si el formato no es valido. */
    static LocalTime horaProgramada(String hora) {
        if (hora == null || hora.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(hora.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String obtenerCodigoPlan(Integer idEmpresa) {
        List<HistorialSuscripcion> activas = suscripciones.findActivasPorEmpresa(idEmpresa, LocalDate.now(ZONA));
        if (activas.isEmpty()) {
            return "gratuito";
        }
        return PlanPolicy.defaults(activas.get(0).getPlan()).codigo();
    }
}
