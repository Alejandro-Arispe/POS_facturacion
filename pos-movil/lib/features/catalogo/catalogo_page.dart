import 'package:flutter/material.dart';

import '../../app/router.dart';
import '../../core/api_service.dart';
import '../../core/constants/app_palette.dart';
import '../carrito/carrito_controller.dart';
import 'catalogo_controller.dart';
import 'producto_card.dart';
import 'producto_model.dart';

class CatalogoPage extends StatefulWidget {
  const CatalogoPage({super.key});

  @override
  State<CatalogoPage> createState() => _CatalogoPageState();
}

class _CatalogoPageState extends State<CatalogoPage> {
  final CatalogoController _controller = CatalogoController();
  final CarritoController _carrito = CarritoController.instance;

  @override
  void initState() {
    super.initState();
    _ejecutar(_controller.cargar);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _ejecutar(Future<void> Function() accion) async {
    try {
      await accion();
    } on ApiException catch (e) {
      _mensaje(e.message);
    }
  }

  void _mensaje(String texto) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(texto)));
  }

  void _agregar(Producto producto) {
    if (_carrito.agregar(producto)) {
      _mensaje('${producto.nombre} agregado al carrito');
    } else {
      _mensaje('El carrito tiene productos de otra empresa. Confirma o vacía ese pedido primero.');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Catálogo'),
        actions: [
          ListenableBuilder(
            listenable: _carrito,
            builder: (context, _) => IconButton(
              tooltip: 'Carrito',
              onPressed: () => Navigator.of(context).pushNamed(AppRouter.carrito),
              icon: Badge(
                isLabelVisible: !_carrito.vacio,
                label: Text('${_carrito.cantidadTotal}'),
                child: const Icon(Icons.shopping_cart_outlined),
              ),
            ),
          ),
        ],
      ),
      body: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) {
          if (_controller.cargando && _controller.productos.isEmpty) {
            return const Center(child: CircularProgressIndicator());
          }
          return RefreshIndicator(
            onRefresh: () => _ejecutar(_controller.recargar),
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                if (_controller.empresas.length > 1) ...[
                  _selectorEmpresa(),
                  const SizedBox(height: 12),
                ],
                if (_controller.productos.isEmpty)
                  _EstadoVacio(error: _controller.error)
                else
                  for (final p in _controller.productos)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: ProductoCard(producto: p, onAgregar: () => _agregar(p)),
                    ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _selectorEmpresa() {
    return DropdownButtonFormField<int>(
      initialValue: _controller.empresa?.idEmpresa,
      decoration: const InputDecoration(labelText: 'Empresa'),
      items: [
        for (final e in _controller.empresas)
          DropdownMenuItem(value: e.idEmpresa, child: Text(e.nombre)),
      ],
      onChanged: (id) {
        final nueva = _controller.empresas.firstWhere((e) => e.idEmpresa == id);
        _ejecutar(() => _controller.seleccionarEmpresa(nueva));
      },
    );
  }
}

class _EstadoVacio extends StatelessWidget {
  final String? error;

  const _EstadoVacio({this.error});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 64),
      child: Column(
        children: [
          Icon(
            error == null ? Icons.inventory_2_outlined : Icons.error_outline,
            size: 48,
            color: AppPalette.textSoft,
          ),
          const SizedBox(height: 12),
          Text(
            error == null ? 'No hay productos en el catálogo' : 'No se pudo cargar el catálogo',
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 4),
          const Text(
            'Desliza hacia abajo para actualizar.',
            style: TextStyle(color: AppPalette.textSoft),
          ),
        ],
      ),
    );
  }
}
