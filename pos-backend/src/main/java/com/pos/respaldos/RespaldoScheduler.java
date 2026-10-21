package com.pos.respaldos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.pos.empresas.domain.Empresa;
import com.pos.empresas.repo.EmpresaRepository;

@Component
public class RespaldoScheduler {

    private static final Logger log = LoggerFactory.getLogger(RespaldoScheduler.class);

    private final EmpresaRepository empresaRepository;
    private final RespaldoService respaldoService;

    public RespaldoScheduler(EmpresaRepository empresaRepository, RespaldoService respaldoService) {
        this.empresaRepository = empresaRepository;
        this.respaldoService = respaldoService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void respaldarTodasLasEmpresas() {
        var empresas = empresaRepository.findAll();
        if (empresas.isEmpty()) {
            return;
        }
        for (Empresa empresa : empresas) {
            Integer idEmpresa = empresa.getIdEmpresa();
            try {
                respaldoService.exportarProgramado(idEmpresa);
            } catch (Exception ex) {
                log.error("No se pudo respaldar la empresa {}: {}", idEmpresa, ex.getMessage());
            }
        }
    }
}
