import 'package:flutter/material.dart';

import '../../app/router.dart';
import '../../core/api_service.dart';
import '../../core/constants/app_palette.dart';
import '../../shared/widgets/estado_views.dart';
import '../catalogo/producto_model.dart';
import 'factura_model.dart';

class FacturasPage extends StatefulWidget {
  const FacturasPage({super.key});

  @override
  State<FacturasPage> createState() => _FacturasPageState();
}

class _FacturasPageState extends State<FacturasPage> {
  final ApiService _api = ApiService();
  List<Factura> _facturas = [];
  bool _cargando = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      final data = await _api.get('/api/facturas/me');
      _facturas = data is List
          ? data.map((j) => Factura.fromJson(j as Map<String, dynamic>)).toList()
          : [];
    } on ApiException catch (e) {
      _error = e.message;
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => _cargando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Mis Facturas')),
      body: _cargando && _facturas.isEmpty
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _cargar,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  if (_error != null && _facturas.isEmpty)
                    EstadoListaView(
                      icon: Icons.error_outline,
                      titulo: 'No se pudieron cargar tus facturas',
                      mensaje: _error!,
                      onReintentar: _cargar,
                    )
                  else if (_facturas.isEmpty)
                    const EstadoListaView(
                      icon: Icons.description_outlined,
                      titulo: 'Aún no tienes facturas',
                      mensaje: 'Las facturas de tus compras aparecerán aquí.',
                    )
                  else
                    for (final f in _facturas)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: _FacturaCard(factura: f),
                      ),
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
