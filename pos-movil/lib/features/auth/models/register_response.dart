class RegisterResponse {
  final int usuarioId;
  final String email;
  final bool activo;
  final String mensaje;
  final bool emailEnviado;

  const RegisterResponse({
    required this.usuarioId,
    required this.email,
    required this.activo,
    required this.mensaje,
    required this.emailEnviado,
  });

  factory RegisterResponse.fromJson(Map<String, dynamic> json) {
    return RegisterResponse(
      usuarioId: int.tryParse(json['usuario_id']?.toString() ?? '') ?? 0,
      email: json['email']?.toString() ?? '',
      activo: json['activo'] == true,
      mensaje: json['mensaje']?.toString() ?? '',
      emailEnviado: json['email_enviado'] == true,
    );
  }
}