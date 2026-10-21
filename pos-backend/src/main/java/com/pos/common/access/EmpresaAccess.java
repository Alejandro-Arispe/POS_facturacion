package com.pos.common.access;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pos.common.exception.ApiException;
import com.pos.empresas.domain.Empresa;
import com.pos.empresas.domain.Sucursal;
import com.pos.empresas.repo.EmpresaRepository;
import com.pos.empresas.repo.SucursalRepository;
import com.pos.usuarios.domain.RolPermiso;
import com.pos.usuarios.domain.Usuario;
import com.pos.usuarios.domain.UsuarioRol;
import com.pos.usuarios.repo.PermisoRepository;
import com.pos.usuarios.repo.RolPermisoRepository;
import com.pos.usuarios.repo.UsuarioRolRepository;

@Component
public class EmpresaAccess {

    private final EmpresaRepository empresaRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final PermisoRepository permisoRepository;
    private final RolPermisoRepository rolPermisoRepository;

    public EmpresaAccess(
            EmpresaRepository empresaRepository,
            SucursalRepository sucursalRepository,
            UsuarioRolRepository usuarioRolRepository,
            PermisoRepository permisoRepository,
            RolPermisoRepository rolPermisoRepository) {
        this.empresaRepository = empresaRepository;
        this.sucursalRepository = sucursalRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.permisoRepository = permisoRepository;
        this.rolPermisoRepository = rolPermisoRepository;
    }

    public void requireUsuarioActivo(Usuario usuario) {
        if (usuario == null || !Boolean.TRUE.equals(usuario.getActivo())) {
            throw ApiException.unauthorized("Usuario no autorizado o inactivo.");
        }
    }

    @Transactional(readOnly = true)
    public Empresa requireEmpresa(Usuario usuario, Integer idEmpresa) {
        requireUsuarioActivo(usuario);
        Empresa empresa = empresaRepository.findById(idEmpresa)
                .orElseThrow(() -> ApiException.notFound("Empresa no encontrada."));
        boolean pertenece = usuarioRolRepository.existsByUsuario_IdUsuarioAndIdEmpresaAndActivoTrue(
                usuario.getIdUsuario(), idEmpresa);
        if (!pertenece) {
            throw ApiException.notFound("Empresa no encontrada para este usuario.");
        }
        if (!Boolean.TRUE.equals(empresa.getActivo())) {
            throw ApiException.forbidden("La empresa fue suspendida por el administrador del sistema.");
        }
        return empresa;
    }

    @Transactional(readOnly = true)
    public Sucursal requireSucursal(Usuario usuario, Integer idEmpresa, Integer idSucursal) {
        requireEmpresa(usuario, idEmpresa);
        return sucursalRepository.findByIdSucursalAndEmpresa_IdEmpresa(idSucursal, idEmpresa)
                .orElseThrow(() -> ApiException.notFound("Sucursal no encontrada para esta empresa."));
    }

    @Transactional(readOnly = true)
    public void requirePermiso(Usuario usuario, Integer idEmpresa, String codigo) {
        requireEmpresa(usuario, idEmpresa);
        if (!permisoRepository.existsByCodigo(codigo)) {
            return;
        }
        List<UsuarioRol> roles = usuarioRolRepository.findByUsuario_IdUsuarioAndIdEmpresaAndActivoTrue(
                usuario.getIdUsuario(), idEmpresa);
        for (UsuarioRol ur : roles) {
            List<RolPermiso> permisos = rolPermisoRepository.findByRol_IdRolAndActivoTrue(ur.getRol().getIdRol());
            for (RolPermiso rp : permisos) {
                if (rp.getPermiso() != null && codigo.equals(rp.getPermiso().getCodigo())) {
                    return;
                }
            }
        }
        throw ApiException.forbidden("No tienes el permiso '" + codigo + "' requerido para esta accion.");
    }
}
