class MetodoPagoModel {
  final int idMetodoPago;
  final String nombre;
  final String? descripcion;

  const MetodoPagoModel({
    required this.idMetodoPago,
    required this.nombre,
    this.descripcion,
  });

  factory MetodoPagoModel.fromJson(Map<String, dynamic> json) {
    return MetodoPagoModel(
      idMetodoPago: int.tryParse(json['id_metodo_pago']?.toString() ?? '') ?? 0,
      nombre: json['nombre']?.toString() ?? '',
      descripcion: json['descripcion']?.toString(),
    );
  }
}

class ProductDetailCredit {
  final int idProducto;
  final String nombre;
  final String? codigoBarra;
  final String unidadMedida;

  const ProductDetailCredit({
    required this.idProducto,
    required this.nombre,
    this.codigoBarra,
    required this.unidadMedida,
  });

  factory ProductDetailCredit.fromJson(Map<String, dynamic> json) {
    return ProductDetailCredit(
      idProducto: int.tryParse(json['id_producto']?.toString() ?? '') ?? 0,
      nombre: json['nombre']?.toString() ?? '',
      codigoBarra: json['codigo_barra']?.toString(),
      unidadMedida: json['unidad_medida']?.toString() ?? '',
    );
  }
}

class SaleDetailCredit {
  final int idDetalleVenta;
  final int idProducto;
  final int cantidad;
  final double precioUnitario;
  final double descuento;
  final double subtotal;
  final double total;
  final String? descripcion;
  final ProductDetailCredit? producto;

  const SaleDetailCredit({
    required this.idDetalleVenta,
    required this.idProducto,
    required this.cantidad,
    required this.precioUnitario,
    required this.descuento,
    required this.subtotal,
    required this.total,
    this.descripcion,
    this.producto,
  });

  factory SaleDetailCredit.fromJson(Map<String, dynamic> json) {
    final prodJson = json['producto'];
    return SaleDetailCredit(
      idDetalleVenta: int.tryParse(json['id_detalle_venta']?.toString() ?? '') ?? 0,
      idProducto: int.tryParse(json['id_producto']?.toString() ?? '') ?? 0,
      cantidad: int.tryParse(json['cantidad']?.toString() ?? '') ?? 0,
      precioUnitario: double.tryParse(json['precio_unitario']?.toString() ?? '') ?? 0.0,
      descuento: double.tryParse(json['descuento']?.toString() ?? '') ?? 0.0,
      subtotal: double.tryParse(json['subtotal']?.toString() ?? '') ?? 0.0,
      total: double.tryParse(json['total']?.toString() ?? '') ?? 0.0,
      descripcion: json['descripcion']?.toString(),
      producto: prodJson != null ? ProductDetailCredit.fromJson(prodJson as Map<String, dynamic>) : null,
    );
  }
}

class SaleCredit {
  final int idVenta;
  final int idTipoVenta;
  final int idCliente;
  final int idCajaSesion;
  final int idUsuario;
  final double subtotal;
  final double descuentoTotal;
  final double total;
  final DateTime fecha;
  final String estado;
  final String? tipoVentaNombre;
  final List<SaleDetailCredit> detalles;

  const SaleCredit({
    required this.idVenta,
    required this.idTipoVenta,
    required this.idCliente,
    required this.idCajaSesion,
    required this.idUsuario,
    required this.subtotal,
    required this.descuentoTotal,
    required this.total,
    required this.fecha,
    required this.estado,
    this.tipoVentaNombre,
    required this.detalles,
  });

  factory SaleCredit.fromJson(Map<String, dynamic> json) {
    final detailsJson = json['detalles'] as List?;
    return SaleCredit(
      idVenta: int.tryParse(json['id_venta']?.toString() ?? '') ?? 0,
      idTipoVenta: int.tryParse(json['id_tipo_venta']?.toString() ?? '') ?? 0,
      idCliente: int.tryParse(json['id_cliente']?.toString() ?? '') ?? 0,
      idCajaSesion: int.tryParse(json['id_caja_sesion']?.toString() ?? '') ?? 0,
      idUsuario: int.tryParse(json['id_usuario']?.toString() ?? '') ?? 0,
      subtotal: double.tryParse(json['subtotal']?.toString() ?? '') ?? 0.0,
      descuentoTotal: double.tryParse(json['descuento_total']?.toString() ?? '') ?? 0.0,
      total: double.tryParse(json['total']?.toString() ?? '') ?? 0.0,
      fecha: DateTime.tryParse(json['fecha']?.toString() ?? '') ?? DateTime.now(),
      estado: json['estado']?.toString() ?? '',
      tipoVentaNombre: json['tipo_venta_nombre']?.toString(),
      detalles: detailsJson != null
          ? detailsJson.map((d) => SaleDetailCredit.fromJson(d as Map<String, dynamic>)).toList()
          : [],
    );
  }
}

class CreditPayment {
  final int idPagoCredito;
  final int idMetodoPago;
  final double montoPagado;
  final DateTime fechaPago;
  final MetodoPagoModel? metodoPago;

  const CreditPayment({
    required this.idPagoCredito,
    required this.idMetodoPago,
    required this.montoPagado,
    required this.fechaPago,
    this.metodoPago,
  });

  factory CreditPayment.fromJson(Map<String, dynamic> json) {
    final methodJson = json['metodo_pago'];
    return CreditPayment(
      idPagoCredito: int.tryParse(json['id_pago_credito']?.toString() ?? '') ?? 0,
      idMetodoPago: int.tryParse(json['id_metodo_pago']?.toString() ?? '') ?? 0,
      montoPagado: double.tryParse(json['monto_pagado']?.toString() ?? '') ?? 0.0,
      fechaPago: DateTime.tryParse(json['fecha_pago']?.toString() ?? '') ?? DateTime.now(),
      metodoPago: methodJson != null ? MetodoPagoModel.fromJson(methodJson as Map<String, dynamic>) : null,
    );
  }
}

class AccountReceivable {
  final int idCxc;
  final int idVenta;
  final double montoCredito;
  final double saldoPendiente;
  final DateTime fechaInicio;
  final DateTime fechaVencimiento;
  final String estado;
  final SaleCredit venta;
  final List<CreditPayment> pagosCredito;

  const AccountReceivable({
    required this.idCxc,
    required this.idVenta,
    required this.montoCredito,
    required this.saldoPendiente,
    required this.fechaInicio,
    required this.fechaVencimiento,
    required this.estado,
    required this.venta,
    required this.pagosCredito,
  });

  factory AccountReceivable.fromJson(Map<String, dynamic> json) {
    final saleJson = json['venta'];
    final paymentsJson = json['pagos_credito'] as List?;
    return AccountReceivable(
      idCxc: int.tryParse(json['id_cxc']?.toString() ?? '') ?? 0,
      idVenta: int.tryParse(json['id_venta']?.toString() ?? '') ?? 0,
      montoCredito: double.tryParse(json['monto_credito']?.toString() ?? '') ?? 0.0,
      saldoPendiente: double.tryParse(json['saldo_pendiente']?.toString() ?? '') ?? 0.0,
      fechaInicio: DateTime.tryParse(json['fecha_inicio']?.toString() ?? '') ?? DateTime.now(),
      fechaVencimiento: DateTime.tryParse(json['fecha_vencimiento']?.toString() ?? '') ?? DateTime.now(),
      estado: json['estado']?.toString() ?? '',
      venta: SaleCredit.fromJson(saleJson as Map<String, dynamic>),
      pagosCredito: paymentsJson != null
          ? paymentsJson.map((p) => CreditPayment.fromJson(p as Map<String, dynamic>)).toList()
          : [],
    );
  }
}
