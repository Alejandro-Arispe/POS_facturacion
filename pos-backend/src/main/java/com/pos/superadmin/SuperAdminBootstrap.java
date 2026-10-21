package com.pos.superadmin;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pos.common.security.PasswordService;
import com.pos.usuarios.domain.Persona;
import com.pos.usuarios.domain.Usuario;
import com.pos.usuarios.repo.PersonaRepository;
import com.pos.usuarios.repo.UsuarioRepository;

/**
 * Garantiza que exista la cuenta SuperAdmin del sistema (la que maneja el proveedor del SaaS).
 * Si el correo ya esta registrado solo lo marca como SuperAdmin; si no existe y se configuro
 * una contrasena (SUPERADMIN_PASSWORD), crea la cuenta.
 */
@Component
@Order(100)
public class SuperAdminBootstrap implements ApplicationRunner {

	private final UsuarioRepository usuarioRepository;
	private final PersonaRepository personaRepository;
	private final PasswordService passwordService;
	private final String email;
	private final String password;

	public SuperAdminBootstrap(
			UsuarioRepository usuarioRepository,
			PersonaRepository personaRepository,
			PasswordService passwordService,
			@Value("${app.superadmin.email:}") String email,
			@Value("${app.superadmin.password:}") String password) {
		this.usuarioRepository = usuarioRepository;
		this.personaRepository = personaRepository;
		this.passwordService = passwordService;
		this.email = email;
		this.password = password;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (email == null || email.isBlank()) {
			return;
		}
		Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
		if (usuario == null) {
			if (password == null || password.isBlank()) {
				System.out.println("[SUPERADMIN] " + email + " no existe y no hay SUPERADMIN_PASSWORD; se omite.");
				return;
			}
			Persona persona = new Persona();
			persona.setNombreCompleto("Super Administrador");
			persona.setFechaNacimiento(LocalDate.of(1990, 1, 1));
			persona.setGenero("N/E");
			persona.setTelefono("00000000");
			persona.setDocumento("SUPERADMIN");
			personaRepository.save(persona);
			usuario = new Usuario();
			usuario.setPersona(persona);
			usuario.setEmail(email.trim());
			usuario.setContrasena(passwordService.hash(password));
			usuario.setFechaCreacion(LocalDate.now(ZoneOffset.UTC));
			usuario.setActivo(Boolean.TRUE);
			usuario.setCodigoVerificacionIntentos(0);
		}
		if (!usuario.esSuperAdmin() || !Boolean.TRUE.equals(usuario.getActivo())) {
			usuario.setSuperAdmin(Boolean.TRUE);
			usuario.setActivo(Boolean.TRUE);
			usuarioRepository.save(usuario);
			System.out.println("[SUPERADMIN] Cuenta SuperAdmin habilitada: " + usuario.getEmail());
		}
	}
}
