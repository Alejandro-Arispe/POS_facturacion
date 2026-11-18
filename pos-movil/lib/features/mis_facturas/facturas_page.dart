import 'package:flutter/material.dart';

import '../../app/router.dart';
import '../../core/api_service.dart';
import '../../core/constants/app_palette.dart';
import '../../shared/widgets/estado_views.dart';
import '../catalogo/producto_model.dart';
import '../mis_compras/compra_model.dart';
import 'factura_model.dart';

class FacturasPage extends StatefulWidget {
  /// Cuando la pestaña queda visible (IndexedStack), recarga la lista.
  final bool active;

  const FacturasPage({super.key, this.active = true});

  @override
  State<FacturasPage> createState() => _FacturasPageState();
}

class _FacturasPageState extends State<FacturasPage> {
  final ApiService _api = ApiService();
  List<Factura> _facturas = [];
  List<Compra> _comprasSinFactura = [];
  bool _cargando = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  @override
  void didUpdateWidget(covariant FacturasPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.active && !oldWidget.active) {
      _cargar();
    }
  }

  Future<void> _cargar() async {
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      final results = await Future.wait([
        _api.get('/api/facturas/me'),
        _api.get('/api/ventas/me'),
      ]);
      final facturasRaw = results[0];
      final ventasRaw = results[1];

      _facturas = facturasRaw is List
          ? facturasRaw.map((j) => Factura.fromJson(j as Map<String, dynamic>)).toList()
          : [];

      final compras = ventasRaw is List
          ? ventasRaw.map((j) => Compra.fromJson(j as Map<String, dynamic>)).toList()
          : <Compra>[];
      final idsFactura = _facturas.map((f) => f.idFactura).toSet();
      _comprasSinFactura = compras
          .where((c) => c.idFactura == null || !idsFactura.contains(c.idFactura))
          .where((c) => c.idFactura == null)
          .toList();
    } on ApiException catch (e) {
      _error = e.message;
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => _cargando = false);
    }
  }

  void _irACompras() {
    // Mis Compras = índice 2 del HomeShell
    Navigator.of(context).pushNamedAndRemoveUntil(
      AppRouter.home,
      (route) => false,
      arguments: 2,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Mis Facturas')),
      body: _cargando && _facturas.isEmpty && _comprasSinFactura.isEmpty
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _cargar,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  if (_error != null && _facturas.isEmpty && _comprasSinFactura.isEmpty)
                    EstadoListaView(
                      icon: Icons.error_outline,
                      titulo: 'No se pudieron cargar tus facturas',
                      mensaje: _error!,
                      onReintentar: _cargar,
                    )
                  else ...[
                    if (_facturas.isEmpty)
                      EstadoListaView(
                        icon: Icons.description_outlined,
                        titulo: 'Aún no tienes facturas electrónicas',
                        mensaje: _comprasSinFactura.isEmpty
                            ? 'Cuando una compra se facture en caja (factura en línea), aparecerá aquí.'
                            : 'Tienes compras pagadas, pero en caja no se emitió factura SIAT. '
                                'Revisa Mis Compras; la próxima venta debe marcar “Factura en línea”.',
                      )
                    else
                      for (final f in _facturas)
                        Padding(
                          padding: const EdgeInsets.only(bottom: 12),
                          child: _FacturaCard(factura: f),
                        ),
                    if (_comprasSinFactura.isNotEmpty) ...[
                      const SizedBox(height: 8),
                      const Text(
                        'Compras sin factura electrónica',
                        style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
                      ),
                      const SizedBox(height: 4),
                      const Text(
                        'Estas ventas están en Mis Compras, pero el POS no generó factura.',
                        style: TextStyle(color: AppPalette.textSoft, fontSize: 13),
                      ),
                      const SizedBox(height: 12),
                      for (final c in _comprasSinFactura)
                        Padding(
                          padding: const EdgeInsets.only(bottom: 12),
                          child: _CompraSinFacturaCard(
                            compra: c,
                            onTap: () => Navigator.of(context).pushNamed(
                              AppRouter.compraDetalle,
                              arguments: c,
                            ),
                          ),
                        ),
                      OutlinedButton.icon(
                        onPressed: _irACompras,
                        icon: const Icon(Icons.receipt_long_outlined),
                        label: const Text('Ir a Mis Compras'),
                      ),
                    ],
                  ],
                ],
              ),
            ),
    );
  }
}

class _FacturaCard extends StatelessWidget {
  final Factura factura;

  const _FacturaCard({required this.factura});

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => Navigator.of(context).pushNamed(AppRouter.facturaDetalle, arguments: factura),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Factura ${factura.numeroTexto}',
                        style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
                    const SizedBox(height: 4),
                    Text(formatoFecha(factura.fechaEmision),
                        style: const TextStyle(color: AppPalette.textSoft, fontSize: 13)),
                    const SizedBox(height: 8),
                    Text(formatoBs(factura.montoTotal),
                        style: const TextStyle(
                            fontSize: 16, fontWeight: FontWeight.w800, color: AppPalette.primary)),
                  ],
                ),
              ),
              EstadoChip(texto: factura.estadoTexto, color: factura.estadoColor),
              const SizedBox(width: 4),
              const Icon(Icons.chevron_right, color: AppPalette.textSoft),
            ],
          ),
        ),
      ),
    );
  }
}

class _CompraSinFacturaCard extends StatelessWidget {
  final Compra compra;
  final VoidCallback onTap;

  const _CompraSinFacturaCard({required this.compra, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Compra #${compra.idVenta}',
                        style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
                    const SizedBox(height: 4),
                    Text(formatoFecha(compra.fecha),
                        style: const TextStyle(color: AppPalette.textSoft, fontSize: 13)),
                    const SizedBox(height: 8),
                    Text(formatoBs(compra.total),
                        style: const TextStyle(
                            fontSize: 16, fontWeight: FontWeight.w800, color: AppPalette.primary)),
                  ],
                ),
              ),
              const EstadoChip(texto: 'SIN FACTURA', color: AppPalette.warning),
              const SizedBox(width: 4),
              const Icon(Icons.chevron_right, color: AppPalette.textSoft),
            ],
          ),
        ),
      ),
    );
  }
}
