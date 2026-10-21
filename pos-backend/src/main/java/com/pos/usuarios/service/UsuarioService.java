package com.pos.usuarios.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.auditoria.service.BitacoraService;
import com.pos.common.exception.ApiException;
import com.pos.common.mail.EmailService;
import com.pos.common.security.JwtService;
import com.pos.common.security.PasswordService;
import com.pos.usuarios.domain.Persona;
import com.pos.usuarios.domain.Rol;
import com.pos.usuarios.domain.Usuario;
import com.pos.usuarios.dto.MensajeResponse;
import com.pos.usuarios.dto.TokenResponse;
import com.pos.usuarios.dto.UsuarioForgotPassword;
import com.pos.usuarios.dto.UsuarioLogin;
import com.pos.usuarios.dto.UsuarioRegister;
import com.pos.usuarios.dto.UsuarioRegisterResponse;
import com.pos.usuarios.dto.UsuarioVerifyCode;
import com.pos.usuarios.dto.UsuarioVerifyResponse;
import com.pos.usuarios.repo.PersonaRepository;
import com.pos.usuarios.repo.UsuarioRepository;
import com.pos.usuarios.repo.UsuarioRolRepository;

@Service
public class UsuarioService {

    public static final String PASSWORD_RECOVERY_MESSAGE =
            "Si el correo esta registrado, recibiras una nueva contrasena en tu email.";

    private final UsuarioRepository usuarioRepository;
    private final PersonaRepository personaRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final int expirationMinutes;
    private final int maxAttempts;
    private final boolean autoActivateOnEmailFail;
    private final BitacoraService bitacoraService;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            PersonaRepository personaRepository,
            UsuarioRolRepository usuarioRolRepository,
            PasswordService passwordService,
            JwtService jwtService,
            EmailService emailService,
            @Value("${app.verification.expiration-minutes}") int expirationMinutes,
            @Value("${app.verification.max-attempts}") int maxAttempts,
            @Value("${app.auth.auto-activate-on-email-fail:false}") boolean autoActivateOnEmailFail,
            BitacoraService bitacoraService) {
        this.usuarioRepository = usuarioRepository;
        this.personaRepository = personaRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.expirationMinutes = expirationMinutes;
        this.maxAttempts = maxAttempts;
        this.autoActivateOnEmailFail = autoActivateOnEmailFail;
        this.bitacoraService = bitacoraService;
    }

    @Transactional
    public UsuarioRegisterResponse registrar(UsuarioRegister datos) {
        usuarioRepository.findByEmailIgnoreCase(datos.email()).ifPresent(existing -> {
            throw ApiException.badRequest("El correo electronico ya esta registrado.");
        });

        Persona persona = new Persona();
        persona.setNombreCompleto(datos.nombreCompleto());
        persona.setFechaNacimiento(datos.fechaNacimiento());
        persona.setGenero(datos.genero());
        persona.setTelefono(datos.telefono());
        persona.setDocumento(datos.documento());
        personaRepository.save(persona);

        Usuario usuario = new Usuario();
        usuario.setPersona(persona);
        usuario.setFechaCreacion(LocalDate.now(ZoneOffset.UTC));
        usuario.setEmail(datos.email());
        usuario.setContrasena(passwordService.hash(datos.contrasena()));
        usuario.setActivo(Boolean.FALSE);
        usuario.setCodigoVerificacionIntentos(0);

        String codigo = passwordService.randomAlphanumeric(6);
        usuario.setCodigoVerificacionHash(passwordService.sha256Hex(codigo));
        usuario.setCodigoVerificacionExpiraEn(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(expirationMinutes));
        usuarioRepository.save(usuario);

        System.out.println("[POS] Codigo de verificacion para " + datos.email() + ": " + codigo);

        boolean emailEnviado = emailService.sendVerificationEmail(
                datos.email(),
                datos.nombreCompleto(),
                codigo);
        if (!emailEnviado && autoActivateOnEmailFail) {
            usuario.setActivo(Boolean.TRUE);
            usuario.setCodigoVerificacionHash(null);
            usuario.setCodigoVerificacionExpiraEn(null);
            usuario.setCodigoVerificacionIntentos(0);
            usuarioRepository.save(usuario);
        }

        return new UsuarioRegisterResponse(
                usuario.getIdUsuario(),
                usuario.getEmail(),
                Boolean.TRUE.equals(usuario.getActivo()),
                "Registro exitoso. Por favor verifica tu correo para activar tu cuenta.",
                emailEnviado);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(UsuarioLogin datos) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(datos.email()).orElse(null);
        if (usuario == null) {
            registrarLogin(null, datos.email(), "LOGIN_FALLIDO", "Correo no registrado");
            throw ApiException.badRequest("Correo o contrasena incorrectos.");
        }

        if (!passwordService.matches(datos.contrasena(), usuario.getContrasena())) {
            registrarLogin(usuario, null, "LOGIN_FALLIDO", "Contrasena incorrecta");
            throw ApiException.badRequest("Correo o contrasena incorrectos.");
        }
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            registrarLogin(usuario, null, "LOGIN_FALLIDO", "Usuario inactivo");
            throw ApiException.badRequest("Usuario no encontrado o inactivo");
        }

        List<String> roles = new ArrayList<>();
        if (usuario.esSuperAdmin()) {
            roles.add("SUPERADMIN");
        }
        usuarioRolRepository.findByUsuario_IdUsuarioAndActivoTrue(usuario.getIdUsuario()).forEach(ur -> {
            Rol rol = ur.getRol();
            if (rol != null && rol.getNombre() != null && !rol.getNombre().isBlank()) {
                roles.add(rol.getNombre());
            }
        });

        String token = jwtService.createAccessToken(usuario.getIdUsuario(), usuario.getEmail(), roles);
        registrarLogin(usuario, null, "LOGIN", "Inicio de sesion exitoso");
        return new TokenResponse(token, "bearer");
    }

    private void registrarLogin(Usuario usuario, String email, String accion, String detalle) {
        bitacoraService.registrar(new BitacoraService.Evento(
                usuario, email, null, accion, "AUTENTICACION", "POST", "/api/auth/login", null, detalle));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public UsuarioVerifyResponse verificarCodigo(UsuarioVerifyCode datos) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(datos.email())
                .orElseThrow(() -> ApiException.badRequest("No existe un usuario con ese correo."));

        if (Boolean.TRUE.equals(usuario.getActivo())) {
            return new UsuarioVerifyResponse(usuario.getEmail(), true, "La cuenta ya esta activa.");
        }

        if (usuario.getCodigoVerificacionHash() == null || usuario.getCodigoVerificacionExpiraEn() == null) {
            throw ApiException.badRequest("No hay un codigo de verificacion pendiente para este usuario.");
        }

        int intentos = usuario.getCodigoVerificacionIntentos() == null ? 0 : usuario.getCodigoVerificacionIntentos();
        if (intentos >= maxAttempts) {
            throw ApiException.badRequest("Superaste el maximo de intentos. Solicita un nuevo codigo.");
        }

        if (LocalDateTime.now(ZoneOffset.UTC).isAfter(usuario.getCodigoVerificacionExpiraEn())) {
            throw ApiException.badRequest("El codigo de verificacion ha expirado. Solicita uno nuevo.");
        }

        String codigoHash = passwordService.sha256Hex(datos.codigo());
        if (!codigoHash.equals(usuario.getCodigoVerificacionHash())) {
            intentos += 1;
            usuario.setCodigoVerificacionIntentos(intentos);
            usuarioRepository.saveAndFlush(usuario);
            int restantes = maxAttempts - intentos;
            if (restantes <= 0) {
                throw ApiException.badRequest("Superaste el maximo de intentos. Solicita un nuevo codigo.");
            }
            throw ApiException.badRequest("Codigo incorrecto. Te quedan " + restantes + " intento(s).");
        }

        usuario.setActivo(Boolean.TRUE);
        usuario.setCodigoVerificacionHash(null);
        usuario.setCodigoVerificacionExpiraEn(null);
        usuario.setCodigoVerificacionIntentos(0);
        usuarioRepository.save(usuario);

        return new UsuarioVerifyResponse(
                usuario.getEmail(),
                true,
                "Cuenta verificada y activada correctamente.");
    }

    @Transactional
    public MensajeResponse forgotPassword(UsuarioForgotPassword datos) {
        usuarioRepository.findByEmailIgnoreCase(datos.email()).ifPresent(usuario -> {
            String nuevaContrasena = passwordService.randomAlphanumeric(12);
            String nombre = usuario.getEmail();
            if (usuario.getPersona() != null && usuario.getPersona().getNombreCompleto() != null) {
                nombre = usuario.getPersona().getNombreCompleto();
            }
            boolean emailEnviado = emailService.sendPasswordRecoveryEmail(
                    usuario.getEmail(),
                    nombre,
                    nuevaContrasena);
            if (emailEnviado) {
                usuario.setContrasena(passwordService.hash(nuevaContrasena));
                usuarioRepository.save(usuario);
            }
        });
        return new MensajeResponse(PASSWORD_RECOVERY_MESSAGE);
    }
}
