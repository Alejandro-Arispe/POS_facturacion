package com.pos.usuarios.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pos.usuarios.dto.MensajeResponse;
import com.pos.usuarios.dto.TokenResponse;
import com.pos.usuarios.dto.UsuarioForgotPassword;
import com.pos.usuarios.dto.UsuarioLogin;
import com.pos.usuarios.dto.UsuarioRegister;
import com.pos.usuarios.dto.UsuarioRegisterResponse;
import com.pos.usuarios.dto.UsuarioVerifyCode;
import com.pos.usuarios.dto.UsuarioVerifyResponse;
import com.pos.usuarios.service.UsuarioService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioService usuarioService;

    public AuthController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping("/register")
    public UsuarioRegisterResponse register(@RequestBody UsuarioRegister datos) {
        return usuarioService.registrar(datos);
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody UsuarioLogin datos) {
        return usuarioService.login(datos);
    }

    @PostMapping("/verify-code")
    public UsuarioVerifyResponse verifyCode(@RequestBody UsuarioVerifyCode datos) {
        return usuarioService.verificarCodigo(datos);
    }

    @PostMapping("/forgot-password")
    public MensajeResponse forgotPassword(@RequestBody UsuarioForgotPassword datos) {
        return usuarioService.forgotPassword(datos);
    }
}
