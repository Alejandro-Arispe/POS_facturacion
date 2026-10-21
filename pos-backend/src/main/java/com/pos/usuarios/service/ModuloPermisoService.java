package com.pos.usuarios.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.common.security.SecurityUtils;
import com.pos.usuarios.domain.Modulo;
import com.pos.usuarios.domain.Permiso;
import com.pos.usuarios.dto.PermisoSimpleResponse;
import com.pos.usuarios.dto.PermisosPorModuloResponse;
import com.pos.usuarios.repo.ModuloRepository;
import com.pos.usuarios.repo.PermisoRepository;

@Service
public class ModuloPermisoService {

    private final ModuloRepository moduloRepository;
    private final PermisoRepository permisoRepository;

    public ModuloPermisoService(ModuloRepository moduloRepository, PermisoRepository permisoRepository) {
        this.moduloRepository = moduloRepository;
        this.permisoRepository = permisoRepository;
    }

    @Transactional(readOnly = true)
    public List<PermisosPorModuloResponse> listarPermisosPorModulo() {
        SecurityUtils.currentUser();

        List<Modulo> modulos = moduloRepository.findAllByOrderByIdModuloAsc();
        List<Permiso> permisos = permisoRepository.findAllWithModulo();

        Map<Integer, List<PermisoSimpleResponse>> porModulo = new LinkedHashMap<>();
        for (Permiso permiso : permisos) {
            if (permiso.getModulo() == null) {
                continue;
            }
            porModulo
                    .computeIfAbsent(permiso.getModulo().getIdModulo(), key -> new ArrayList<>())
                    .add(new PermisoSimpleResponse(permiso.getIdPermiso(), permiso.getCodigo(), permiso.getNombre()));
        }

        List<PermisosPorModuloResponse> response = new ArrayList<>();
        for (Modulo modulo : modulos) {
            response.add(new PermisosPorModuloResponse(
                    modulo.getIdModulo(),
                    modulo.getCodigo(),
                    modulo.getNombre(),
                    porModulo.getOrDefault(modulo.getIdModulo(), List.of())));
        }
        return response;
    }
}
