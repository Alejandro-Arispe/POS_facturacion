import 'dart:async';

import 'package:flutter/material.dart';

import '../../app/router.dart';
import '../../core/api_service.dart';
import '../../core/constants/app_palette.dart';
import '../../core/services/fcm_global_service.dart';
import '../../shared/widgets/estado_views.dart';
import '../catalogo/producto_model.dart';
import 'pedido_model.dart';

class PedidosPage extends StatefulWidget {
  const PedidosPage({super.key});

  @override
  State<PedidosPage> createState() => _PedidosPageState();
}

class _PedidosPageState extends State<PedidosPage> {
  final ApiService _api = ApiService();
  List<Pedido> _pedidos = [];
  bool _cargando = true;
  String? _error;
  StreamSubscription<dynamic>? _pushSub;
  StreamSubscription<dynamic>? _tapSub;

  @override
  void initState() {
    super.initState();
    _cargar();
    _pushSub = FcmGlobalService.instance.pedidoUpdates.listen((_) {
      if (mounted) _cargar();
    });
    // Tap en un push de pedido (app en background): recargar el estado.
    _tapSub = FcmGlobalService.instance.notificationTaps.listen((data) {
      if (mounted && FcmGlobalService.isPedidoNotification(data)) _cargar();
    });
  }

  @override
  void dispose() {
    _pushSub?.cancel();
    _tapSub?.cancel();
    super.dispose();
  }

  Future<void> _cargar() async {
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      final data = await _api.get('/api/pedidos/me');
      _pedidos = data is List
          ? data.map((j) => Pedido.fromJson(j as Map<String, dynamic>)).toList()
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
      appBar: AppBar(title: const Text('Mis Pedidos')),
      body: _cargando && _pedidos.isEmpty
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _cargar,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  if (_error != null && _pedidos.isEmpty)
                    EstadoListaView(
                      icon: Icons.error_outline,
                      titulo: 'No se pudieron cargar tus pedidos',
                      mensaje: _error!,
                      onReintentar: _cargar,
                    )
                  else if (_pedidos.isEmpty)
                    const EstadoListaView(
                      icon: Icons.local_shipping_outlined,
                      titulo: 'Aún no tienes pedidos',
                      mensaje:
                          'Confirma un pedido desde el carrito y sigue aquí su estado.',
                    )
                  else
                    for (final p in _pedidos)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: _PedidoCard(
                          pedido: p,
                          onChanged: _cargar,
                        ),
                      ),
                ],
              ),
            ),
    );
  }
}

class _PedidoCard extends StatelessWidget {
  final Pedido pedido;
  final Future<void> Function() onChanged;

  const _PedidoCard({required this.pedido, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () async {
          await Navigator.of(context).pushNamed(
            AppRouter.pedidoDetalle,
            arguments: pedido,
          );
          await onChanged();
        },
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Pedido #${pedido.idPedido}',
                        style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
                    const SizedBox(height: 4),
                    Text(formatoFecha(pedido.fechaCreacion),
                        style: const TextStyle(color: AppPalette.textSoft, fontSize: 13)),
                    const SizedBox(height: 8),
                    Text(formatoBs(pedido.totalEstimado),
                        style: const TextStyle(
                            fontSize: 16, fontWeight: FontWeight.w800, color: AppPalette.primary)),
                  ],
                ),
              ),
              EstadoChip(texto: pedido.estadoTexto, color: pedido.estadoColor),
              const SizedBox(width: 4),
              const Icon(Icons.chevron_right, color: AppPalette.textSoft),
            ],
          ),
        ),
      ),
    );
  }
}
