package com.pos.empresas.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pos.common.exception.ApiException;
import com.pos.empresas.dto.InvitacionAceptadaResult;
import com.pos.empresas.service.SucursalService;

@RestController
@RequestMapping("/api/invitaciones")
public class InvitacionController {

	private final SucursalService sucursalService;

	public InvitacionController(SucursalService sucursalService) {
		this.sucursalService = sucursalService;
	}

	@GetMapping(value = "/empleado/aceptar/{token}", produces = MediaType.TEXT_HTML_VALUE)
	public ResponseEntity<String> aceptarEmpleado(@PathVariable String token) {
		try {
			InvitacionAceptadaResult resultado = sucursalService.aceptarInvitacionEmpleado(token);
			return html(HttpStatus.OK, renderEmpleado(resultado.mensaje(), resultado.empresaNombre(), false));
		} catch (ApiException ex) {
			return html(ex.getStatus(), renderEmpleado(ex.getMessage(), "", true));
		} catch (Exception ex) {
			return html(HttpStatus.INTERNAL_SERVER_ERROR, renderEmpleado("Error al aceptar la invitacion.", "", true));
		}
	}

	@GetMapping(value = "/cliente/aceptar/{idEmpresa}/{idUsuario}", produces = MediaType.TEXT_HTML_VALUE)
	public ResponseEntity<String> aceptarCliente(
			@PathVariable Integer idEmpresa,
			@PathVariable Integer idUsuario) {
		try {
			InvitacionAceptadaResult resultado = sucursalService.aceptarInvitacionCliente(idEmpresa, idUsuario);
			return html(HttpStatus.OK, renderCliente(resultado.mensaje(), resultado.empresaNombre(), false));
		} catch (ApiException ex) {
			return html(ex.getStatus(), renderCliente(ex.getMessage(), "", true));
		} catch (Exception ex) {
			return html(HttpStatus.INTERNAL_SERVER_ERROR, renderCliente("Error al aceptar la invitacion.", "", true));
		}
	}

	private ResponseEntity<String> html(HttpStatus status, String body) {
		return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(body);
	}

	private String renderEmpleado(String mensaje, String empresaNombre, boolean esError) {
		String color = esError ? "#d9534f" : "#007bff";
		String titulo = esError ? "No se pudo completar la invitacion" : "Invitacion de empleado";
		String badge = esError ? "!" : "OK";
		String empresaBloque = empresaNombre == null || empresaNombre.isBlank()
				? ""
				: "<p style=\"color: #666; font-size: 14px; line-height: 1.7; margin: 0 0 24px 0;\">Empresa: <strong>"
						+ escape(empresaNombre) + "</strong></p>";
		return """
				<html>
				    <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px; margin: 0;">
				        <div style="background-color: white; padding: 24px; border-radius: 8px; max-width: 640px; margin: 0 auto; box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);">
				            <div style="width: 56px; height: 56px; border-radius: 50%%; background-color: %s; color: white; display: flex; align-items: center; justify-content: center; font-size: 20px; font-weight: 700; margin-bottom: 18px;">
				                %s
				            </div>
				            <h2 style="color: #333; margin: 0 0 12px 0;">%s</h2>
				            <p style="color: #666; font-size: 16px; line-height: 1.7; margin: 0 0 12px 0;">%s</p>
				            %s
				            <div style="padding-top: 12px; border-top: 1px solid #eee; color: #999; font-size: 12px; line-height: 1.6;">
				                Esta confirmacion fue generada automaticamente al abrir el enlace de invitacion.
				            </div>
				        </div>
				    </body>
				</html>
				""".formatted(color, badge, titulo, escape(mensaje), empresaBloque);
	}

	private String renderCliente(String mensaje, String empresaNombre, boolean esError) {
		String color = esError ? "#d9534f" : "#007bff";
		String titulo = esError ? "No se pudo completar la invitacion" : "Invitacion de cliente";
		String badge = esError ? "?" : "?";
		String empresaBloque = empresaNombre == null || empresaNombre.isBlank()
				? ""
				: "<p style=\"color: #666; font-size: 14px; line-height: 1.7; margin: 0 0 24px 0;\">Empresa: <strong>"
						+ empresaNombre + "</strong></p>";
		return """
				<html>
				    <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px; margin: 0;">
				        <div style="background-color: white; padding: 24px; border-radius: 8px; max-width: 640px; margin: 0 auto; box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);">
				            <div style="width: 56px; height: 56px; border-radius: 50%%; background-color: %s; color: white; display: flex; align-items: center; justify-content: center; font-size: 28px; margin-bottom: 18px;">
				                %s
				            </div>
				            <h2 style="color: #333; margin: 0 0 12px 0;">%s</h2>
				            <p style="color: #666; font-size: 16px; line-height: 1.7; margin: 0 0 12px 0;">%s</p>
				            %s
				            <div style="padding-top: 12px; border-top: 1px solid #eee; color: #999; font-size: 12px; line-height: 1.6;">
				                Esta confirmacion fue generada automaticamente al abrir el enlace de invitacion.
				            </div>
				        </div>
				    </body>
				</html>
				""".formatted(color, badge, titulo, mensaje, empresaBloque);
	}

	private String escape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;");
	}
}
