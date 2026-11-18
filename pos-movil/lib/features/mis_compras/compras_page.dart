import 'package:flutter/material.dart';

import '../../app/router.dart';
import '../../core/api_service.dart';
import '../../core/constants/app_palette.dart';
import '../../shared/widgets/estado_views.dart';
import '../catalogo/producto_model.dart';
import 'compra_model.dart';

class ComprasPage extends StatefulWidget {
  const ComprasPage({super.key});

  @override
  State<ComprasPage> createState() => _ComprasPageState();
}

class _ComprasPageState extends State<ComprasPage> {
  final ApiService _api = ApiService();
  List<Compra> _compras = [];
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
      final data = await _api.get('/api/ventas/me');
      _compras = data is List
          ? data.map((j) => Compra.fromJson(j as Map<String, dynamic>)).toList()
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
      appBar: AppBar(title: const Text('Mis Compras')),
      body: _cargando && _compras.isEmpty
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _cargar,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  if (_error != null && _compras.isEmpty)
                    EstadoListaView(
                      icon: Icons.error_outline,
                      titulo: 'No se pudieron cargar tus compras',
                      mensaje: _error!,
                      onReintentar: _cargar,
                    )
                  else if (_compras.isEmpty)
                    const EstadoListaView(
                      icon: Icons.receipt_long_outlined,
                      titulo: 'Aún no tienes compras',
                      mensaje: 'Cuando compres en una de tus empresas, aparecerán aquí.',
                    )
                  else
                    for (final c in _compras)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: _CompraCard(compra: c),
                      ),
                ],
              ),
            ),
    );
  }
}

class _CompraCard extends StatelessWidget {
  final Compra compra;

  const _CompraCard({required this.compra});

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => Navigator.of(context).pushNamed(AppRouter.compraDetalle, arguments: compra),
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
              EstadoChip(texto: compra.estadoTexto, color: compra.estadoColor),
              const SizedBox(width: 4),
              const Icon(Icons.chevron_right, color: AppPalette.textSoft),
            ],
          ),
        ),
      ),
    );
  }
}
