import 'package:flutter/material.dart';

import '../../core/constants/app_palette.dart';
import '../../shared/widgets/estado_views.dart';
import '../catalogo/producto_model.dart';
import 'compra_model.dart';

class CompraDetallePage extends StatelessWidget {
  final Compra compra;

  const CompraDetallePage({super.key, required this.compra});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Compra #${compra.idVenta}')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  Row(
                    children: [
                      const Expanded(
                        child: Text('Resumen',
                            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                      ),
                      EstadoChip(texto: compra.estadoTexto, color: compra.estadoColor),
                    ],
                  ),
                  const SizedBox(height: 8),
                  DatoFila('Fecha', formatoFecha(compra.fecha)),
                  DatoFila('Forma de pago', compra.formaPago),
                  if (compra.tipoVenta != null) DatoFila('Tipo de venta', compra.tipoVenta!),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Productos',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                  const SizedBox(height: 8),
                  if (compra.detalles.isEmpty)
                    const Text('Sin detalle de productos.',
                        style: TextStyle(color: AppPalette.textSoft)),
                  for (final d in compra.detalles) ...[
                    _DetalleFila(detalle: d),
                    const Divider(height: 16, color: AppPalette.border),
                  ],
                  DatoFila('Subtotal', formatoBs(compra.subtotal)),
                  if (compra.descuento > 0) DatoFila('Descuento', '- ${formatoBs(compra.descuento)}'),
                  DatoFila('Total', formatoBs(compra.total), destacado: true),
                ],
              ),
            ),
          ),
          if (compra.pagos.isNotEmpty) ...[
            const SizedBox(height: 12),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Pagos',
                        style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                    const SizedBox(height: 8),
                    for (final p in compra.pagos) DatoFila(p.metodo, formatoBs(p.monto)),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _DetalleFila extends StatelessWidget {
  final CompraDetalle detalle;

  const _DetalleFila({required this.detalle});

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(detalle.producto, style: const TextStyle(fontWeight: FontWeight.w600)),
              const SizedBox(height: 2),
              Text(
                '${detalle.cantidad} × ${formatoBs(detalle.precioUnitario)}'
                '${detalle.descuento > 0 ? '  (desc. ${formatoBs(detalle.descuento)})' : ''}',
                style: const TextStyle(color: AppPalette.textSoft, fontSize: 13),
              ),
            ],
          ),
        ),
        Text(formatoBs(detalle.total), style: const TextStyle(fontWeight: FontWeight.w700)),
      ],
    );
  }
}
