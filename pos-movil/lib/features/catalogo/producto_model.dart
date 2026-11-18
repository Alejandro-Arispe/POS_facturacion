/// Producto tal como lo devuelve GET /api/empresas/{id}/productos para un cliente.
class Producto {
  final int idProducto;
  final int idEmpresa;
  final String nombre;
  final String? descripcion;
  final double precio;
  final bool disponible;

  /// Sucursal en la que se calcula [disponible]; el pedido se envía a esta sucursal.
  final int? idSucursal;

  const Producto({
    required this.idProducto,
    required this.idEmpresa,
    required this.nombre,
    this.descripcion,
    required this.precio,
    required this.disponible,
    this.idSucursal,
  });

  factory Producto.fromJson(Map<String, dynamic> json) {
    return Producto(
      idProducto: (json['id_producto'] as num).toInt(),
      idEmpresa: (json['id_empresa'] as num).toInt(),
      nombre: (json['nombre'] ?? '').toString(),
      descripcion: json['descripcion']?.toString(),
      precio: double.tryParse('${json['precio']}') ?? 0,
      disponible: json['disponible'] == true,
      idSucursal: (json['id_sucursal'] as num?)?.toInt(),
    );
  }
}

String formatoBs(double monto) => 'Bs ${monto.toStringAsFixed(2)}';
