class ClientCategory {
  final int idCategoriaCliente;
  final int idEmpresa;
  final String nombre;
  final String? descripcion;
  final int plazoCredito;
  final double descuentoBase;
  final double limiteCredito;
  final bool activo;

  const ClientCategory({
    required this.idCategoriaCliente,
    required this.idEmpresa,
    required this.nombre,
    this.descripcion,
    required this.plazoCredito,
    required this.descuentoBase,
    required this.limiteCredito,
    required this.activo,
  });

  factory ClientCategory.fromJson(Map<String, dynamic> json) {
    return ClientCategory(
      idCategoriaCliente: int.tryParse(json['id_categoria_cliente']?.toString() ?? '') ?? 0,
      idEmpresa: int.tryParse(json['id_empresa']?.toString() ?? '') ?? 0,
      nombre: json['nombre']?.toString() ?? '',
      descripcion: json['descripcion']?.toString(),
      plazoCredito: int.tryParse(json['plazo_credito']?.toString() ?? '') ?? 0,
      descuentoBase: double.tryParse(json['descuento_base']?.toString() ?? '') ?? 0.0,
      limiteCredito: double.tryParse(json['limite_credito']?.toString() ?? '') ?? 0.0,
      activo: json['activo'] == true,
    );
  }
}
