import 'package:flutter/material.dart';

import '../../app/router.dart';
import '../../core/api_service.dart';
import '../../core/constants/app_palette.dart';
import '../catalogo/producto_model.dart';
import 'carrito_controller.dart';

class CarritoPage extends StatelessWidget {
  const CarritoPage({super.key});

  CarritoController get _carrito => CarritoController.instance;

  Future<void> _confirmar(BuildContext context) async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      final idPedido = await _carrito.confirmar();
      if (!context.mounted) return;
      await showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          icon: const Icon(Icons.check_circle, color: AppPalette.success, size: 48),
          title: const Text('Pedido registrado'),
          content: Text(
            idPedido == null
                ? 'Tu pedido fue enviado. Síguelo en la pestaña Pedidos.'
                : 'Tu pedido N° $idPedido fue enviado. Síguelo en la pestaña Pedidos.',
            textAlign: TextAlign.center,
          ),
          actions: [
            TextButton(onPressed: () => Navigator.of(ctx).pop(), child: const Text('Aceptar')),
          ],
        ),
      );
      if (!context.mounted) return;
      Navigator.of(context).pushNamedAndRemoveUntil(
        AppRouter.home,
        (route) => false,
        arguments: 0,
      );
    } on ApiException catch (e) {
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Carrito')),
      body: ListenableBuilder(
        listenable: _carrito,
        builder: (context, _) {
          if (_carrito.vacio) {
            return const Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.shopping_cart_outlined, size: 48, color: AppPalette.textSoft),
                  SizedBox(height: 12),
                  Text('Tu carrito está vacío',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                ],
              ),
            );
          }
          return Column(
            children: [
              Expanded(
                child: ListView.separated(
                  padding: const EdgeInsets.all(16),
                  itemCount: _carrito.items.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 12),
                  itemBuilder: (context, i) => _ItemCard(item: _carrito.items[i]),
                ),
              ),
              _Resumen(onConfirmar: () => _confirmar(context)),
            ],
          );
        },
      ),
    );
  }
}

class _ItemCard extends StatelessWidget {
  final CarritoItem item;

  const _ItemCard({required this.item});

  @override
  Widget build(BuildContext context) {
    final carrito = CarritoController.instance;
    final id = item.producto.idProducto;
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 8, 12),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(item.producto.nombre,
                      style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
                  const SizedBox(height: 4),
                  Text('${formatoBs(item.producto.precio)} c/u',
                      style: const TextStyle(color: AppPalette.textSoft, fontSize: 13)),
                  const SizedBox(height: 4),
                  Text('Subtotal: ${formatoBs(item.subtotal)}',
                      style: const TextStyle(fontWeight: FontWeight.w600)),
                ],
              ),
            ),
            IconButton(
              tooltip: 'Quitar uno',
              onPressed: carrito.enviando ? null : () => carrito.cambiarCantidad(id, -1),
              icon: const Icon(Icons.remove_circle_outline),
            ),
            Text('${item.cantidad}',
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
            IconButton(
              tooltip: 'Agregar uno',
              onPressed: carrito.enviando ? null : () => carrito.cambiarCantidad(id, 1),
              icon: const Icon(Icons.add_circle_outline, color: AppPalette.primary),
            ),
          ],
        ),
      ),
    );
  }
}

class _Resumen extends StatelessWidget {
  final VoidCallback onConfirmar;

  const _Resumen({required this.onConfirmar});

  @override
  Widget build(BuildContext context) {
    final carrito = CarritoController.instance;
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
      decoration: const BoxDecoration(
        color: AppPalette.surface,
        border: Border(top: BorderSide(color: AppPalette.border)),
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                const Text('Total estimado',
                    style: TextStyle(fontSize: 15, color: AppPalette.textSoft)),
                const Spacer(),
                Text(formatoBs(carrito.total),
                    style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
              ],
            ),
            const SizedBox(height: 12),
            ElevatedButton(
              onPressed: carrito.enviando ? null : onConfirmar,
              child: carrito.enviando
                  ? const SizedBox(
                      height: 22,
                      width: 22,
                      child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white),
                    )
                  : const Text('Confirmar pedido'),
            ),
          ],
        ),
      ),
    );
  }
}
