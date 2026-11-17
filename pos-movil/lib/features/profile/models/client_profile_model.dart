class ClientProfile {
  final int idCliente;
  final int idUsuario;
  final int? idCategoriaCliente;
  final String codigoCliente;
  final double saldoCredito;
  final double limiteCredito;
  final bool activo;

  const ClientProfile({
    required this.idCliente,
    required this.idUsuario,
    this.idCategoriaCliente,
    required this.codigoCliente,
    required this.saldoCredito,
    required this.limiteCredito,
    required this.activo,
  });

  factory ClientProfile.fromJson(Map<String, dynamic> json) {
    return ClientProfile(
      idCliente: int.tryParse(json['id_cliente']?.toString() ?? '') ?? 0,
      idUsuario: int.tryParse(json['id_usuario']?.toString() ?? '') ?? 0,
      idCategoriaCliente: json['id_categoria_cliente'] != null
          ? int.tryParse(json['id_categoria_cliente'].toString())
          : null,
      codigoCliente: json['codigo_cliente']?.toString() ?? '',
      saldoCredito: double.tryParse(json['saldo_credito']?.toString() ?? '') ?? 0.0,
      limiteCredito: double.tryParse(json['limite_credito']?.toString() ?? '') ?? 0.0,
      activo: json['activo'] == true,
    );
  }
}
