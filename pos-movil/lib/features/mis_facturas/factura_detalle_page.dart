import 'dart:io';

import 'package:flutter/material.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../core/api_service.dart';
import '../../core/constants/app_palette.dart';
import '../../shared/widgets/estado_views.dart';
import '../catalogo/producto_model.dart';
import 'factura_model.dart';

class FacturaDetallePage extends StatefulWidget {
  final Factura factura;

  const FacturaDetallePage({super.key, required this.factura});

  @override
  State<FacturaDetallePage> createState() => _FacturaDetallePageState();
}

class _FacturaDetallePageState extends State<FacturaDetallePage> {
  final ApiService _api = ApiService();
  bool _descargando = false;

  Factura get _f => widget.factura;

  void _mensaje(String texto) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(texto)));
  }

  /// GET /api/facturas/me/{id}/pdf → archivo temporal → visor de PDF del sistema.
  Future<void> _descargarPdf() async {
    setState(() => _descargando = true);
    try {
      final bytes = await _api.getBytes('/api/facturas/me/${_f.idFactura}/pdf');
      if (bytes.isEmpty) throw const ApiException('El PDF de la factura está vacío.');
      final dir = await getTemporaryDirectory();
      final file = File('${dir.path}/Factura-${_f.numero ?? _f.idFactura}.pdf');
      await file.writeAsBytes(bytes, flush: true);
      final result = await OpenFilex.open(file.path, type: 'application/pdf');
      if (result.type != ResultType.done) {
        _mensaje(result.type == ResultType.noAppToOpen
            ? 'No hay una aplicación para abrir PDF en este dispositivo.'
            : 'No se pudo abrir el PDF: ${result.message}');
      }
    } on ApiException catch (e) {
      _mensaje(e.message);
    } on FileSystemException catch (e) {
      _mensaje('No se pudo guardar el PDF: ${e.message}');
    } finally {
      if (mounted) setState(() => _descargando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Factura ${_f.numeroTexto}')),
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
                        child: Text('Datos de la factura',
                            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                      ),
                      EstadoChip(texto: _f.estadoTexto, color: _f.estadoColor),
                    ],
                  ),
                  const SizedBox(height: 8),
                  DatoFila('Número', _f.numeroTexto),
                  DatoFila('Fecha de emisión', formatoFecha(_f.fechaEmision)),
                  if (_f.nitEmisor != null) DatoFila('NIT emisor', _f.nitEmisor!),
                  DatoFila('Cliente', _f.nombreCliente ?? '—'),
                  DatoFila('NIT/CI', _f.nitCliente ?? '—'),
                  const Divider(height: 20, color: AppPalette.border),
                  DatoFila('Subtotal', formatoBs(_f.montoTotal - _f.iva)),
                  DatoFila('IVA 13%', formatoBs(_f.iva)),
                  DatoFila('Total', formatoBs(_f.montoTotal), destacado: true),
                  if (_f.cuf != null) ...[
                    const Divider(height: 20, color: AppPalette.border),
                    const Align(
                      alignment: Alignment.centerLeft,
                      child: Text('CUF', style: TextStyle(color: AppPalette.textSoft)),
                    ),
                    const SizedBox(height: 4),
                    SelectableText(_f.cuf!,
                        style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
                  ],
                ],
              ),
            ),
          ),
          if (_f.qrCodeData != null) ...[
            const SizedBox(height: 12),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    const Text('Código QR de verificación',
                        style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                    const SizedBox(height: 12),
                    QrImageView(
                      data: _f.qrCodeData!,
                      size: 200,
                      backgroundColor: Colors.white,
                    ),
                  ],
                ),
              ),
            ),
          ],
          const SizedBox(height: 16),
          ElevatedButton.icon(
            onPressed: _descargando ? null : _descargarPdf,
            icon: _descargando
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white),
                  )
                : const Icon(Icons.picture_as_pdf_outlined),
            label: const Text('Descargar PDF'),
          ),
        ],
      ),
    );
  }
}
