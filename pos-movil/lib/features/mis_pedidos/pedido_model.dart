import 'package:flutter/material.dart';

import '../../core/constants/app_palette.dart';
import '../../shared/widgets/estado_views.dart';

class PedidoDetalle {
  final int idDetalle;
  final int idProducto;
  final int cantidad;
  final double precioUnitarioEstimado;
  final double descuentoEstimado;
  final double subtotalEstimado;

  const PedidoDetalle({
    required this.idDetalle,
    required this.idProducto,
    required this.cantidad,
    required this.precioUnitarioEstimado,
    required this.descuentoEstimado,
    required this.subtotalEstimado,
  });

  factory PedidoDetalle.fromJson(Map<String, dynamic> json) => PedidoDetalle(
        idDetalle: (json['id_detalle'] as num?)?.toInt() ?? 0,
        idProducto: (json['id_producto'] as num?)?.toInt() ?? 0,
        cantidad: (json['cantidad'] as num?)?.toInt() ?? 0,
        precioUnitarioEstimado: aDouble(json['precio_unitario_estimado']),
        descuentoEstimado: aDouble(json['descuento_estimado']),
        subtotalEstimado: aDouble(json['subtotal_estimado']),
      );

  String get productoTexto => 'Producto #$idProducto';
}

/// Pedido del cliente (GET /api/pedidos/me).
class Pedido {
  final int idPedido;
  final int idEmpresa;
  final int idSucursal;
  final int? idVenta;
  final String estado;
  final double subtotalEstimado;
  final double descuentoEstimado;
  final double totalEstimado;
  final String? observacionCliente;
  final String? observacionEmpresa;
  final DateTime? fechaCreacion;
  final List<PedidoDetalle> detalles;

  const Pedido({
    required this.idPedido,
    required this.idEmpresa,
    required this.idSucursal,
    this.idVenta,
    required this.estado,
    required this.subtotalEstimado,
    required this.descuentoEstimado,
    required this.totalEstimado,
    this.observacionCliente,
    this.observacionEmpresa,
    this.fechaCreacion,
    required this.detalles,
  });

  factory Pedido.fromJson(Map<String, dynamic> json) => Pedido(
        idPedido: (json['id_pedido'] as num?)?.toInt() ?? 0,
        idEmpresa: (json['id_empresa'] as num?)?.toInt() ?? 0,
        idSucursal: (json['id_sucursal'] as num?)?.toInt() ?? 0,
        idVenta: (json['id_venta'] as num?)?.toInt(),
        estado: (json['estado'] ?? '').toString(),
        subtotalEstimado: aDouble(json['subtotal_estimado']),
        descuentoEstimado: aDouble(json['descuento_estimado']),
        totalEstimado: aDouble(json['total_estimado']),
        observacionCliente: json['observacion_cliente']?.toString(),
        observacionEmpresa: json['observacion_empresa']?.toString(),
        fechaCreacion: DateTime.tryParse('${json['fecha_creacion'] ?? ''}'),
        detalles: (json['detalles'] as List? ?? const [])
            .map((d) => PedidoDetalle.fromJson(d as Map<String, dynamic>))
            .toList(),
      );

  bool get puedeCancelar =>
      estado == 'enviado' || estado == 'en_revision';

  String get estadoTexto => switch (estado) {
        'enviado' => 'Enviado',
        'en_revision' => 'En revisión',
        'confirmado' => 'Confirmado',
        'en_preparacion' => 'En preparación',
        'listo_para_recoger' => 'Listo para recoger',
        'convertido_en_venta' => 'Convertido en venta',
        'entregado' => 'Entregado',
        'cancelado' => 'Cancelado',
        'rechazado' => 'Rechazado',
        final otro => otro.isEmpty ? '—' : otro,
      };

  Color get estadoColor => switch (estado) {
        'enviado' || 'en_revision' => AppPalette.warning,
        'confirmado' || 'en_preparacion' || 'listo_para_recoger' => AppPalette.primary,
        'convertido_en_venta' || 'entregado' => AppPalette.success,
        'cancelado' || 'rechazado' => AppPalette.danger,
        _ => AppPalette.textSoft,
      };
}
