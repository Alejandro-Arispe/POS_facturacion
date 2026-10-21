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
            List<String> tablas,
            boolean incluyeVentas,
            String mensaje) {
    }

    public record Resumen(String archivo, LocalDateTime fecha) {
    }

    public record Restaurado(
            String archivo,
            Integer idEmpresa,
            List<String> tablas,
            boolean incluyeVentas,
            String mensaje) {
    }
}
