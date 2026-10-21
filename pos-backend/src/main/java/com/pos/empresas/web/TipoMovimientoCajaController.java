package com.pos.empresas.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pos.empresas.dto.TipoMovimientoCajaResponse;
import com.pos.empresas.repo.TipoMovimientoCajaRepository;

@RestController
@RequestMapping("/api/empresas")
public class TipoMovimientoCajaController {

    private final TipoMovimientoCajaRepository tipoMovimientoCajaRepository;

    public TipoMovimientoCajaController(TipoMovimientoCajaRepository tipoMovimientoCajaRepository) {
        this.tipoMovimientoCajaRepository = tipoMovimientoCajaRepository;
    }

    @GetMapping("/tipos-movimiento-caja")
    public List<TipoMovimientoCajaResponse> listar() {
        return tipoMovimientoCajaRepository.findAllByOrderByIdTipoMovimientoCajaAsc().stream()
                .map(TipoMovimientoCajaResponse::from)
                .toList();
    }
}
