package com.pos.usuarios.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.common.exception.ApiException;
import com.pos.usuarios.domain.Permiso;
import com.pos.usuarios.domain.Rol;
import com.pos.usuarios.domain.RolPermiso;
import com.pos.usuarios.dto.RolCreateRequest;
import com.pos.usuarios.dto.RolCreateResponse;
import com.pos.usuarios.dto.RolDetalleResponse;
import com.pos.usuarios.dto.RolPermisoResponse;
import com.pos.usuarios.dto.RolResponse;
import com.pos.usuarios.dto.RolUpdateRequest;
import com.pos.usuarios.repo.PermisoRepository;
import com.pos.usuarios.repo.RolPermisoRepository;
import com.pos.usuarios.repo.RolRepository;

@Service
public class RolService {

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final RolPermisoRepository rolPermisoRepository;

    public RolService(
            RolRepository rolRepository,
            PermisoRepository permisoRepository,
            RolPermisoRepository rolPermisoRepository) {
        this.rolRepository = rolRepository;
        this.permisoRepository = permisoRepository;
        this.rolPermisoRepository = rolPermisoRepository;
    }

    @Transactional(readOnly = true)
    public List<RolResponse> listarPorEmpresa(Integer idEmpresa) {
        return rolRepository.findActivosVisiblesPorEmpresa(idEmpresa).stream()
                .map(RolResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RolDetalleResponse obtenerPorId(Integer idRol) {
        Rol rol = rolRepository.findById(idRol)
                .orElseThrow(() -> ApiException.notFound("No existe el rol solicitado."));
        return toDetalle(rol);
    }

    @Transactional
    public RolDetalleResponse editar(Integer idRol, RolUpdateRequest request) {
        Rol rol = rolRepository.findById(idRol)
                .orElseThrow(() -> ApiException.notFound("No existe el rol solicitado."));

        boolean activo = Boolean.TRUE.equals(request.activo());
        List<Integer> permisoIdsUnicos = uniqueIds(request.permisoIds());
        Map<Integer, Permiso> permisos = cargarPermisos(permisoIdsUnicos);

        try {
            boolean estabaActivo = Boolean.TRUE.equals(rol.getActivo());
            rol.setActivo(activo);
            rolRepository.save(rol);

            List<RolPermiso> existentes = rolPermisoRepository.findByRol_IdRol(rol.getIdRol());
            Map<Integer, RolPermiso> porPermisoId = new LinkedHashMap<>();
            for (RolPermiso rp : existentes) {
                if (rp.getPermiso() != null) {
                    porPermisoId.put(rp.getPermiso().getIdPermiso(), rp);
                }
            }

            if (!activo) {
                for (RolPermiso rp : porPermisoId.values()) {
                    rp.setActivo(Boolean.FALSE);
                }
            } else if (!estabaActivo && permisoIdsUnicos.isEmpty()) {
                for (RolPermiso rp : porPermisoId.values()) {
                    rp.setActivo(Boolean.TRUE);
                }
            } else {
                for (Integer idPermiso : permisoIdsUnicos) {
                    RolPermiso rp = porPermisoId.get(idPermiso);
                    if (rp == null) {
                        RolPermiso nuevo = new RolPermiso();
                        nuevo.setRol(rol);
                        nuevo.setPermiso(permisos.get(idPermiso));
                        nuevo.setActivo(Boolean.TRUE);
                        rp = rolPermisoRepository.save(nuevo);
                        porPermisoId.put(idPermiso, rp);
                    } else {
                        rp.setActivo(Boolean.TRUE);
                    }
                }
                Set<Integer> enviados = new LinkedHashSet<>(permisoIdsUnicos);
                for (Map.Entry<Integer, RolPermiso> entry : porPermisoId.entrySet()) {
                    if (!enviados.contains(entry.getKey())) {
                        entry.getValue().setActivo(Boolean.FALSE);
                    }
                }
            }
            rolPermisoRepository.saveAll(porPermisoId.values());
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.badRequest("No se pudo editar el rol.");
        }

        return obtenerPorId(idRol);
    }

    @Transactional
    public RolCreateResponse crear(Integer idEmpresa, RolCreateRequest request) {
        String nombre = request.nombre() == null ? "" : request.nombre().strip().toUpperCase();
        List<Integer> permisoIdsUnicos = uniqueIds(request.permisoIds());
        Map<Integer, Permiso> permisos = cargarPermisos(permisoIdsUnicos);

        try {
            Rol rol = new Rol();
            rol.setNombre(nombre);
            rol.setIdEmpresa(idEmpresa);
            rol.setTipo("CUSTOM");
            rol.setDescripcion(nombre);
            rol.setActivo(Boolean.TRUE);
            rolRepository.saveAndFlush(rol);

            for (Integer idPermiso : permisoIdsUnicos) {
                RolPermiso rp = new RolPermiso();
                rp.setRol(rol);
                rp.setPermiso(permisos.get(idPermiso));
                rp.setActivo(Boolean.TRUE);
                rolPermisoRepository.save(rp);
            }

            return new RolCreateResponse(RolResponse.from(rol), permisoIdsUnicos);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.badRequest(
                    "No se pudo crear el rol. Verifique que el nombre no exista en esta empresa.");
        }
    }

    private RolDetalleResponse toDetalle(Rol rol) {
        List<RolPermisoResponse> rolPermisos = rolPermisoRepository.findByRol_IdRol(rol.getIdRol()).stream()
                .sorted(Comparator.comparing(RolPermiso::getIdRolPermiso, Comparator.nullsLast(Integer::compareTo)))
                .map(RolPermisoResponse::from)
                .toList();
        return new RolDetalleResponse(
                rol.getIdRol(),
                rol.getNombre(),
                rol.getIdEmpresa(),
                rol.getTipo(),
                rol.getDescripcion(),
                rol.getActivo(),
                rolPermisos);
    }

    private Map<Integer, Permiso> cargarPermisos(List<Integer> permisoIds) {
        Map<Integer, Permiso> result = new LinkedHashMap<>();
        for (Integer idPermiso : permisoIds) {
            Permiso permiso = permisoRepository.findById(idPermiso)
                    .orElseThrow(() -> ApiException.badRequest("No existe el permiso con id " + idPermiso + "."));
            result.put(idPermiso, permiso);
        }
        return result;
    }

    private List<Integer> uniqueIds(List<Integer> ids) {
        if (ids == null) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }
}
