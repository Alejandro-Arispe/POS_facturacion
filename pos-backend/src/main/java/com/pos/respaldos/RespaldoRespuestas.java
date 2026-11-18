package com.pos.respaldos;

import java.time.LocalDateTime;
import java.util.List;

public final class RespaldoRespuestas {

    private RespaldoRespuestas() {
    }

    public record Generado(
            String archivo,
            LocalDateTime fecha,
            Integer idEmpresa,
            Integer idSucursal,
            String alcance,
            List<String> tablas,
            boolean incluyeVentas,
            String mensaje) {
    }

    public record Resumen(
            String archivo,
            LocalDateTime fecha,
            String alcance,
            Integer idSucursal,
            String nombreSucursal,
            long tamanoBytes) {
    }

    public record Restaurado(
            String archivo,
            Integer idEmpresa,
            Integer idSucursal,
            String alcance,
            List<String> tablas,
            boolean incluyeVentas,
            String mensaje) {
    }

    public record ConfiguracionDTO(
            Integer idConfiguracionRespaldo,
            Integer idEmpresa,
            Boolean activo,
            String frecuencia,
            String hora,
            String alcance,
            Integer idSucursal,
            String emailDestino,
            Boolean enviarEmail,
            LocalDateTime ultimoRespaldoExitoso,
            String planActual,
            boolean permiteAutomatico,
            boolean permiteCloudEmail) {
    }

    public record GuardarConfiguracionDTO(
            Boolean activo,
            String frecuencia,
            String hora,
            String alcance,
            Integer idSucursal,
            String emailDestino,
            Boolean enviarEmail) {
    }

    public record CrearManualDTO(
            Integer idSucursal) {
    }
}
