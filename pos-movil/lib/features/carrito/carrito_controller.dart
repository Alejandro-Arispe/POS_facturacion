import 'package:flutter/foundation.dart';

import '../../core/api_service.dart';
import '../catalogo/producto_model.dart';

class CarritoItem {
  final Producto producto;
  int cantidad;

  CarritoItem(this.producto, this.cantidad);

  double get subtotal => producto.precio * cantidad;
}

/// Carrito en memoria (una sola empresa y sucursal por pedido).
class CarritoController extends ChangeNotifier {
  CarritoController._();

  static final CarritoController instance = CarritoController._();

  final ApiService _api = ApiService();
  final Map<int, CarritoItem> _items = {};
  bool enviando = false;

  List<CarritoItem> get items => _items.values.toList(growable: false);
  bool get vacio => _items.isEmpty;
  int get cantidadTotal => _items.values.fold(0, (s, i) => s + i.cantidad);
  double get total => _items.values.fold(0, (s, i) => s + i.subtotal);
  int? get idEmpresa => vacio ? null : _items.values.first.producto.idEmpresa;

  /// Agrega una unidad. Devuelve false si el producto es de otra empresa o sucursal que el carrito actual.
  bool agregar(Producto producto) {
    if (!vacio) {
      final actual = _items.values.first.producto;
      if (actual.idEmpresa != producto.idEmpresa || actual.idSucursal != producto.idSucursal) {
        return false;
      }
    }
    _items.update(producto.idProducto, (i) => i..cantidad += 1,
        ifAbsent: () => CarritoItem(producto, 1));
    notifyListeners();
    return true;
  }

  void cambiarCantidad(int idProducto, int delta) {
    final item = _items[idProducto];
    if (item == null) return;
    item.cantidad += delta;
    if (item.cantidad <= 0) _items.remove(idProducto);
    notifyListeners();
  }

  void quitar(int idProducto) {
    if (_items.remove(idProducto) != null) notifyListeners();
  }

  void vaciar() {
    _items.clear();
    notifyListeners();
  }

  /// Envía el pedido (POST /api/pedidos/empresas/{id}) y vacía el carrito. Devuelve el id del pedido.
  /// Lanza [ApiException] con el mensaje del backend si falla.
  Future<int?> confirmar() async {
    if (vacio || enviando) return null;
    final primero = _items.values.first.producto;
    enviando = true;
    notifyListeners();
    try {
      final data = await _api.post('/api/pedidos/empresas/${primero.idEmpresa}', {
        'id_sucursal': primero.idSucursal,
        'detalles': _items.values
            .map((i) => {
                  'id_producto': i.producto.idProducto,
                  'cantidad': i.cantidad,
                  'precio_unitario_estimado': i.producto.precio,
                })
            .toList(),
      });
      _items.clear();
      return data is Map ? (data['id_pedido'] as num?)?.toInt() : null;
    } finally {
      enviando = false;
      notifyListeners();
    }
  }
}
