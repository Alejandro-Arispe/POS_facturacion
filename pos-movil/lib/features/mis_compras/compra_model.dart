import 'package:flutter/material.dart';

import '../../core/constants/app_palette.dart';
import '../../shared/widgets/estado_views.dart';

class CompraDetalle {
  final String producto;
  final int cantidad;
  final double precioUnitario;
  final double descuento;
  final double total;

  const CompraDetalle({
    required this.producto,
    required this.cantidad,
    required this.precioUnitario,
    required this.descuento,
    required this.total,
  });

  factory CompraDetalle.fromJson(Map<String, dynamic> json) {
    final producto = json['producto'];
    final nombre = producto is Map ? producto['nombre']?.toString() : null;
    return CompraDetalle(
      producto: nombre ?? json['descripcion']?.toString() ?? 'Producto ${json['id_producto']}',
      cantidad: (json['cantidad'] as num?)?.toInt() ?? 0,
      precioUnitario: aDouble(json['precio_unitario']),
      descuento: aDouble(json['descuento']),
      total: aDouble(json['total'] ?? json['subtotal']),
    );
  }
}

class CompraPago {
  final String metodo;
  final double monto;

  const CompraPago(this.metodo, this.monto);
}

/// Venta del cliente (GET /api/ventas/me).
class Compra {
  final int idVenta;
  final DateTime? fecha;
  final double subtotal;
  final double descuento;
  final double total;
  final String estado;
  final String? tipoVenta;
  final String? metodoPago;
  final int? idFactura;
  final List<CompraDetalle> detalles;
  final List<CompraPago> pagos;

  const Compra({
    required this.idVenta,
    required this.fecha,
    required this.subtotal,
    required this.descuento,
    required this.total,
    required this.estado,
    this.tipoVenta,
    this.metodoPago,
    this.idFactura,
    required this.detalles,
    required this.pagos,
  });

  factory Compra.fromJson(Map<String, dynamic> json) {
    String? nombre(dynamic item) => item is Map ? item['nombre']?.toString() : null;
    return Compra(
      idVenta: (json['id_venta'] as num).toInt(),
      fecha: DateTime.tryParse('${json['fecha'] ?? ''}'),
      subtotal: aDouble(json['subtotal']),
      descuento: aDouble(json['descuento_total']),
      total: aDouble(json['total']),
      estado: (json['estado'] ?? '').toString(),
      tipoVenta: json['tipo_venta_nombre']?.toString(),
      metodoPago: nombre(json['metodo_pago']),
      idFactura: (json['id_factura'] as num?)?.toInt(),
      detalles: (json['detalles'] as List? ?? const [])
          .map((d) => CompraDetalle.fromJson(d as Map<String, dynamic>))
          .toList(),
      pagos: (json['pagos'] as List? ?? const [])
          .whereType<Map>()
          .map((p) => CompraPago(nombre(p['metodo_pago']) ?? 'Pago', aDouble(p['monto'])))
          .toList(),
    );
  }

  /// PENDIENTE / PAGADO / CRÉDITO a partir del estado de la venta.
  String get estadoTexto => switch (estado.toUpperCase()) {
        'CREDITO' => 'CRÉDITO',
        'PAGADA' => 'PAGADO',
        'PENDIENTE_COBRO' => 'PENDIENTE',
        final otro => otro.isEmpty ? '—' : otro,
      };

  Color get estadoColor => switch (estado.toUpperCase()) {
        'CREDITO' => AppPalette.warning,
        'PAGADA' => AppPalette.success,
        _ => AppPalette.textSoft,
      };

  /// Forma de pago: método(s) de los pagos, o "Crédito" si no hubo pago al contado.
  String get formaPago {
    if (pagos.isNotEmpty) return pagos.map((p) => p.metodo).toSet().join(', ');
    if (metodoPago != null) return metodoPago!;
    return estado.toUpperCase() == 'CREDITO' ? 'Crédito' : (tipoVenta ?? '—');
  }
}
