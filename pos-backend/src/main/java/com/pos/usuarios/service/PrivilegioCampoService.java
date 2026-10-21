package com.pos.usuarios.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;
import com.pos.usuarios.domain.PrivilegioCampo;
import com.pos.usuarios.domain.Rol;
import com.pos.usuarios.domain.Usuario;
import com.pos.usuarios.domain.UsuarioRol;
import com.pos.usuarios.dto.CampoEstadoRequest;
import com.pos.usuarios.dto.CampoEstadoResponse;
import com.pos.usuarios.dto.PrivilegioCampoGuardarRequest;
import com.pos.usuarios.repo.PrivilegioCampoRepository;
import com.pos.usuarios.repo.RolRepository;
import com.pos.usuarios.repo.UsuarioRolRepository;

@Service
public class PrivilegioCampoService {

    public static final String FORMULARIO_PRODUCTO = "producto";
    public static final String EDITABLE = "EDITABLE";
    public static final String SOLO_LECTURA = "SOLO_LECTURA";
    public static final String OCULTO = "OCULTO";

    private static final List<String> CAMPOS_PRODUCTO = List.of("costo", "precio", "editar", "eliminar");
    private static final Set<String> ESTADOS = Set.of(EDITABLE, SOLO_LECTURA, OCULTO);

    private final PrivilegioCampoRepository privilegioCampoRepository;
    private final RolRepository rolRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final EmpresaAccess empresaAccess;

    public PrivilegioCampoService(
            PrivilegioCampoRepository privilegioCampoRepository,
            RolRepository rolRepository,
            UsuarioRolRepository usuarioRolRepository,
            EmpresaAccess empresaAccess) {
        this.privilegioCampoRepository = privilegioCampoRepository;
        this.rolRepository = rolRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.empresaAccess = empresaAccess;
    }

    @Transactional(readOnly = true)
    public List<CampoEstadoResponse> listar(Usuario usuario, Integer idRol, String formulario) {
        Rol rol = requireRol(idRol);
        asegurarLectura(usuario, rol);
        return completar(rol.getIdRol(), normalizarFormulario(formulario));
    }

    @Transactional
    public List<CampoEstadoResponse> guardar(Usuario usuario, Integer idRol, PrivilegioCampoGuardarRequest request) {
        Rol rol = requireRol(idRol);
        if (rol.getIdEmpresa() == null) {
            throw ApiException.badRequest("El rol ADMINISTRADOR de sistema no se puede editar por esta via.");
        }
        empresaAccess.requireEmpresa(usuario, rol.getIdEmpresa());

        String formulario = normalizarFormulario(request == null ? null : request.formulario());
        List<CampoEstadoRequest> campos = request == null ? null : request.campos();
        if (campos == null || campos.isEmpty()) {
            throw ApiException.badRequest("Debe indicar los campos del formulario.");
        }

        try {
            for (CampoEstadoRequest item : campos) {
                if (item == null) {
                    throw ApiException.badRequest("Debe indicar el campo y su estado.");
                }
                String campo = normalizarCampo(item.campo());
                String estado = normalizarEstado(item.estado());
                PrivilegioCampo fila = privilegioCampoRepository
                        .findByIdRolAndFormularioAndCampo(rol.getIdRol(), formulario, campo)
                        .orElseGet(() -> {
                            PrivilegioCampo nueva = new PrivilegioCampo();
                            nueva.setIdRol(rol.getIdRol());
                            nueva.setFormulario(formulario);
                            nueva.setCampo(campo);
                            return nueva;
                        });
                fila.setEstado(estado);
                privilegioCampoRepository.save(fila);
            }
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.badRequest("No se pudieron guardar los privilegios del formulario.");
        }

        return completar(rol.getIdRol(), formulario);
    }

    @Transactional(readOnly = true)
    public Map<String, String> misCamposProducto(Usuario usuario, Integer idEmpresa) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        return estadosProducto(usuario, idEmpresa);
    }

    @Transactional(readOnly = true)
    public Map<String, String> estadosProducto(Usuario usuario, Integer idEmpresa) {
        Integer idRol = rolDeUsuarioEnEmpresa(usuario, idEmpresa);
        if (idRol == null) {
            return editables();
        }
        return comoMapa(completar(idRol, FORMULARIO_PRODUCTO));
    }

    public boolean permiteEditar(Map<String, String> estados) {
        return EDITABLE.equals(estado(estados, "editar"));
    }

    public boolean permiteEliminar(Map<String, String> estados) {
        return EDITABLE.equals(estado(estados, "eliminar"));
    }

    public boolean permiteEscribir(Map<String, String> estados, String campo) {
        return EDITABLE.equals(estado(estados, campo));
    }

    public boolean estaOculto(Map<String, String> estados, String campo) {
        return OCULTO.equals(estado(estados, campo));
    }

    private List<CampoEstadoResponse> completar(Integer idRol, String formulario) {
        Map<String, String> porCampo = editables();
        for (PrivilegioCampo fila : privilegioCampoRepository.findByIdRolAndFormulario(idRol, formulario)) {
            if (fila.getCampo() != null && CAMPOS_PRODUCTO.contains(fila.getCampo()) && ESTADOS.contains(fila.getEstado())) {
                porCampo.put(fila.getCampo(), fila.getEstado());
            }
        }
        List<CampoEstadoResponse> resultado = new ArrayList<>();
        for (String campo : CAMPOS_PRODUCTO) {
            resultado.add(new CampoEstadoResponse(campo, porCampo.get(campo)));
        }
        return resultado;
    }

    private Map<String, String> comoMapa(List<CampoEstadoResponse> campos) {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (CampoEstadoResponse campo : campos) {
            mapa.put(campo.campo(), campo.estado());
        }
        return mapa;
    }

    private Map<String, String> editables() {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (String campo : CAMPOS_PRODUCTO) {
            mapa.put(campo, EDITABLE);
        }
        return mapa;
    }

    private Integer rolDeUsuarioEnEmpresa(Usuario usuario, Integer idEmpresa) {
        if (usuario == null || idEmpresa == null) {
            return null;
        }
        List<UsuarioRol> roles = usuarioRolRepository.findActivosDistintoCliente(usuario.getIdUsuario(), idEmpresa);
        if (roles.isEmpty()) {
            roles = usuarioRolRepository.findByUsuario_IdUsuarioAndIdEmpresaAndActivoTrue(
                    usuario.getIdUsuario(), idEmpresa);
        }
        if (roles.isEmpty() || roles.get(0).getRol() == null) {
            return null;
        }
        return roles.get(0).getRol().getIdRol();
    }

    private Rol requireRol(Integer idRol) {
        return rolRepository.findById(idRol)
                .orElseThrow(() -> ApiException.notFound("No existe el rol solicitado."));
    }

    private void asegurarLectura(Usuario usuario, Rol rol) {
        if (rol.getIdEmpresa() == null) {
            empresaAccess.requireUsuarioActivo(usuario);
            return;
        }
        empresaAccess.requireEmpresa(usuario, rol.getIdEmpresa());
    }

    private String normalizarFormulario(String formulario) {
        String valor = formulario == null ? "" : formulario.strip().toLowerCase(Locale.ROOT);
        if (!FORMULARIO_PRODUCTO.equals(valor)) {
            throw ApiException.badRequest("Formulario no soportado.");
        }
        return valor;
    }

    private String normalizarCampo(String campo) {
        if (campo == null || campo.isBlank()) {
            throw ApiException.badRequest("Debe indicar el campo.");
        }
        String valor = campo.strip().toLowerCase(Locale.ROOT);
        if (!CAMPOS_PRODUCTO.contains(valor)) {
            throw ApiException.badRequest("Campo no valido para el formulario de producto.");
        }
        return valor;
    }

    private String normalizarEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            throw ApiException.badRequest("Debe indicar el estado del campo.");
        }
        String valor = estado.strip().toUpperCase(Locale.ROOT);
        if (!ESTADOS.contains(valor)) {
            throw ApiException.badRequest("Estado de campo no valido. Use EDITABLE, SOLO_LECTURA u OCULTO.");
        }
        return valor;
    }

    private String estado(Map<String, String> estados, String campo) {
        if (estados == null || estados.get(campo) == null) {
            return EDITABLE;
        }
        return estados.get(campo);
    }
}
