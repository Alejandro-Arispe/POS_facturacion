class VerifyCodeResponse {
  final String email;
  final bool activo;
  final String mensaje;

  const VerifyCodeResponse({
    required this.email,
    required this.activo,
    required this.mensaje,
  });

  factory VerifyCodeResponse.fromJson(Map<String, dynamic> json) {
    return VerifyCodeResponse(
      email: json['email']?.toString() ?? '',
      activo: json['activo'] == true,
      mensaje: json['mensaje']?.toString() ?? '',
    );
  }
}
