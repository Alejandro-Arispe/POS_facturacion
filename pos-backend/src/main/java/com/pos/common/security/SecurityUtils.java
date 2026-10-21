package com.pos.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.pos.common.exception.ApiException;
import com.pos.usuarios.domain.Usuario;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Usuario currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Usuario usuario)) {
            throw ApiException.unauthorized("No se pudo validar las credenciales");
        }
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw ApiException.unauthorized("Usuario no encontrado o inactivo");
        }
        return usuario;
    }

    public static Usuario currentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Usuario usuario) {
            return usuario;
        }
        return null;
    }
}
