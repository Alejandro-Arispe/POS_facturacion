package com.pos.respaldos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pos.common.mail.EmailService;
import com.pos.empresas.domain.Empresa;
import com.pos.empresas.domain.HistorialSuscripcion;
import com.pos.empresas.repo.EmpresaRepository;
import com.pos.empresas.repo.HistorialSuscripcionRepository;
import com.pos.empresas.service.PlanPolicy;

@Component
public class RespaldoScheduler {

    private static final Logger log = LoggerFactory.getLogger(RespaldoScheduler.class);

    private final EmpresaRepository empresaRepository;
    private final ConfiguracionRespaldoRepository configRepository;
    private final HistorialSuscripcionRepository suscripciones;
    private final RespaldoService respaldoService;
    private final RespaldoAlmacen almacen;
    private final EmailService emailService;

    public RespaldoScheduler(
            EmpresaRepository empresaRepository,
            ConfiguracionRespaldoRepository configRepository,
            HistorialSuscripcionRepository suscripciones,
            RespaldoService respaldoService,
            RespaldoAlmacen almacen,
            EmailService emailService) {
        this.empresaRepository = empresaRepository;
        this.configRepository = configRepository;
        this.suscripciones = suscripciones;
        this.respaldoService = respaldoService;
        this.almacen = almacen;
        this.emailService = emailService;
    }

    /**
     * Revisa cada hora en punto si alguna empresa tiene un respaldo programado para ejecutarse.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void procesarRespaldosProgramados() {
        LocalDateTime ahora = LocalDateTime.now();
        int horaActual = ahora.getHour();
        int minutoActual = ahora.getMinute();

        List<ConfiguracionRespaldo> configs = configRepository.findAll();
        for (ConfiguracionRespaldo config : configs) {
            if (!Boolean.TRUE.equals(config.getActivo())) {
                continue;
            }
            Empresa empresa = config.getEmpresa();
            if (empresa == null || !Boolean.TRUE.equals(empresa.getActivo())) {
                continue;
            }

            Integer idEmpresa = empresa.getIdEmpresa();

            // Validar si el plan SaaS activo permite respaldos automáticos
            String planCodigo = obtenerCodigoPlan(idEmpresa);
            boolean permiteAutomatico = "basico".equalsIgnoreCase(planCodigo) || "pro".equalsIgnoreCase(planCodigo);
            if (!permiteAutomatico) {
                continue;
            }

            if (!tocaEjecutar(config, ahora, horaActual)) {
                continue;
            }

            try {
                Integer idSucursal = "SUCURSAL".equalsIgnoreCase(config.getAlcance()) && config.getSucursal() != null
                        ? config.getSucursal().getIdSucursal()
                        : null;

                RespaldoRespuestas.Generado generado = respaldoService.guardar(idEmpresa, idSucursal);
                config.setUltimoRespaldoExitoso(ahora);
                configRepository.save(config);
                log.info("Respaldo programado exitoso para empresa {}: {}", idEmpresa, generado.archivo());

                // Envío Cloud por Email exclusivo para Plan Pro
                if ("pro".equalsIgnoreCase(planCodigo) && Boolean.TRUE.equals(config.getEnviarEmail()) && config.getEmailDestino() != null && !config.getEmailDestino().isBlank()) {
                    byte[] archivoBytes = almacen.leer(idEmpresa, generado.archivo());
                    emailService.sendBackupEmail(config.getEmailDestino(), empresa.getNombre(), generado.archivo(), archivoBytes);
                    log.info("Respaldo Cloud enviado por email a {} para empresa {}", config.getEmailDestino(), idEmpresa);
                }
            } catch (Exception ex) {
                log.error("Error al ejecutar respaldo automático para empresa {}: {}", idEmpresa, ex.getMessage(), ex);
            }
        }
    }

    private boolean tocaEjecutar(ConfiguracionRespaldo config, LocalDateTime ahora, int horaActual) {
        String horaConfig = config.getHora(); // ej "02:00" o "23:00"
        if (horaConfig != null && !horaConfig.isBlank()) {
            try {
                String[] parts = horaConfig.split(":");
                int horaProg = Integer.parseInt(parts[0].trim());
                if (horaProg != horaActual) {
                    return false;
                }
            } catch (Exception ignored) {
                if (horaActual != 2) return false; // Default 02:00 AM
            }
        } else if (horaActual != 2) {
            return false;
        }

        // Revisar si ya se ejecutó hoy
        LocalDateTime ultimo = config.getUltimoRespaldoExitoso();
        if (ultimo != null && ultimo.toLocalDate().isEqual(ahora.toLocalDate())) {
            return false;
        }

        String frecuencia = config.getFrecuencia() != null ? config.getFrecuencia().toUpperCase() : "DESACTIVADO";
        return switch (frecuencia) {
            case "DIARIO" -> true;
            case "SEMANAL" -> ahora.getDayOfWeek().getValue() == 7; // Domingo
            case "MENSUAL" -> ahora.getDayOfMonth() == 1; // Primer día del mes
            default -> false;
        };
    }

    private String obtenerCodigoPlan(Integer idEmpresa) {
        List<HistorialSuscripcion> activas = suscripciones.findActivasPorEmpresa(idEmpresa, LocalDate.now());
        if (activas.isEmpty()) {
            return "gratuito";
        }
        return PlanPolicy.defaults(activas.get(0).getPlan()).codigo();
    }
}
