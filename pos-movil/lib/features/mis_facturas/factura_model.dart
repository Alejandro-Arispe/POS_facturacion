import 'package:flutter/material.dart';

import '../../core/constants/app_palette.dart';
import '../../shared/widgets/estado_views.dart';

/// Factura del cliente (GET /api/facturas/me).
class Factura {
  final int idFactura;
  final int? idVenta;
  final int? numero;
  final DateTime? fechaEmision;
  final double montoTotal;
  final double iva;
  final String estadoSiat;
  final String? cuf;
  final String? nitEmisor;
  final String? nitCliente;
  final String? nombreCliente;
  final String? qrCodeData;

  const Factura({
    required this.idFactura,
    this.idVenta,
    this.numero,
    this.fechaEmision,
    required this.montoTotal,
    required this.iva,
    required this.estadoSiat,
    this.cuf,
    this.nitEmisor,
    this.nitCliente,
    this.nombreCliente,
    this.qrCodeData,
  });

  factory Factura.fromJson(Map<String, dynamic> json) {
    String? texto(String k) {
      final v = json[k]?.toString();
      return v == null || v.isEmpty ? null : v;
    }

    return Factura(
      idFactura: (json['id_factura'] as num).toInt(),
      idVenta: (json['id_venta'] as num?)?.toInt(),
      numero: (json['numero_factura'] as num?)?.toInt(),
      fechaEmision: DateTime.tryParse('${json['fecha_emision'] ?? ''}'),
      montoTotal: aDouble(json['monto_total']),
      iva: aDouble(json['iva']),
      estadoSiat: (json['estado_siat'] ?? '').toString(),
      cuf: texto('cuf'),
      nitEmisor: texto('nit_emisor'),
      nitCliente: texto('nit_cliente'),
      nombreCliente: texto('nombre_cliente'),
      qrCodeData: texto('qr_code_data'),
    );
  }

  String get numeroTexto => numero == null ? 'S/N' : 'N° $numero';

  /// VÁLIDA / ANULADA; otros estados del SIN se muestran tal cual (p. ej. PENDIENTE).
  String get estadoTexto => switch (estadoSiat.toUpperCase()) {
        'VALIDADA' => 'VÁLIDA',
        'ANULADA' => 'ANULADA',
        final otro => otro.isEmpty ? '—' : otro,
      };

  Color get estadoColor => switch (estadoSiat.toUpperCase()) {
        'VALIDADA' => AppPalette.success,
        'ANULADA' => AppPalette.danger,
        _ => AppPalette.warning,
      };
}
