import 'dart:async';

import 'package:flutter/material.dart';

import '../../core/api_service.dart';
import '../../core/constants/app_palette.dart';
import '../../core/services/fcm_global_service.dart';
import '../../shared/widgets/estado_views.dart';
import '../catalogo/producto_model.dart';
import 'pedido_model.dart';

class PedidoDetallePage extends StatefulWidget {
  final Pedido pedido;

  const PedidoDetallePage({super.key, required this.pedido});

  @override
  State<PedidoDetallePage> createState() => _PedidoDetallePageState();
}

class _PedidoDetallePageState extends State<PedidoDetallePage> {
  final ApiService _api = ApiService();
  late Pedido _pedido = widget.pedido;
  bool _cargando = false;
  bool _cancelando = false;
  StreamSubscription<dynamic>? _pushSub;

  @override
  void initState() {
    super.initState();
    // Solo refresca si el push es de este pedido ("Pedido #12 ..." no debe disparar el #1).
    final propio = RegExp('Pedido #${_pedido.idPedido}\\b');
    _pushSub = FcmGlobalService.instance.pedidoUpdates.listen((m) {
      if (mounted && propio.hasMatch(m.notification?.title ?? '')) _refrescar();
    });
  }

  @override
  void dispose() {
    _pushSub?.cancel();
    super.dispose();
  }

  Future<void> _refrescar() async {
    setState(() => _cargando = true);
    try {
      final data = await _api.get('/api/pedidos/me/${_pedido.idPedido}');
      if (data is Map<String, dynamic>) {
        _pedido = Pedido.fromJson(data);
      }
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => _cargando = false);
    }
  }

  Future<void> _cancelar() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Cancelar pedido'),
        content: Text('¿Cancelar el pedido #${_pedido.idPedido}?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('No')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Sí, cancelar')),
        ],
      ),
    );
    if (ok != true) return;
    setState(() => _cancelando = true);
    try {
      await _api.patch('/api/pedidos/me/${_pedido.idPedido}/cancelar', {});
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Pedido cancelado')),
      );
      await _refrescar();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => _cancelando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Pedido #${_pedido.idPedido}'),
        actions: [
          IconButton(
            tooltip: 'Actualizar',
            onPressed: _cargando ? null : _refrescar,
            icon: _cargando
                ? const SizedBox(
                    width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.refresh),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(formatoFecha(_pedido.fechaCreacion),
                            style: const TextStyle(color: AppPalette.textSoft)),
                      ),
                      EstadoChip(texto: _pedido.estadoTexto, color: _pedido.estadoColor),
                    ],
                  ),
                  const SizedBox(height: 12),
                  _fila('Subtotal estimado', formatoBs(_pedido.subtotalEstimado)),
                  _fila('Descuento estimado', formatoBs(_pedido.descuentoEstimado)),
                  _fila('Total estimado', formatoBs(_pedido.totalEstimado), negrita: true),
                  if (_pedido.observacionCliente?.isNotEmpty == true) ...[
                    const SizedBox(height: 8),
                    Text('Tu nota: ${_pedido.observacionCliente}',
                        style: const TextStyle(color: AppPalette.textSoft)),
                  ],
                  if (_pedido.observacionEmpresa?.isNotEmpty == true) ...[
                    const SizedBox(height: 4),
                    Text('Nota de la empresa: ${_pedido.observacionEmpresa}',
                        style: const TextStyle(color: AppPalette.textSoft)),
                  ],
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          const Text('Detalle', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
          const SizedBox(height: 8),
          for (final d in _pedido.detalles)
            Card(
              child: ListTile(
                title: Text(d.productoTexto),
                subtitle: Text('${d.cantidad} × ${formatoBs(d.precioUnitarioEstimado)}'),
                trailing: Text(formatoBs(d.subtotalEstimado),
                    style: const TextStyle(fontWeight: FontWeight.w700)),
              ),
            ),
          if (_pedido.puedeCancelar) ...[
            const SizedBox(height: 24),
            OutlinedButton(
              onPressed: _cancelando ? null : _cancelar,
              style: OutlinedButton.styleFrom(foregroundColor: AppPalette.danger),
              child: _cancelando
                  ? const SizedBox(
                      width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2))
                  : const Text('Cancelar pedido'),
            ),
          ],
        ],
      ),
    );
  }

  Widget _fila(String label, String valor, {bool negrita = false}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Row(
        children: [
          Expanded(child: Text(label, style: const TextStyle(color: AppPalette.textSoft))),
          Text(valor,
              style: TextStyle(fontWeight: negrita ? FontWeight.w800 : FontWeight.w600)),
        ],
      ),
    );
  }
}
