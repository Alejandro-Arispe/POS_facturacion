import 'package:flutter_test/flutter_test.dart';
import 'package:pos_frontend_mobile/features/carrito/carrito_controller.dart';
import 'package:pos_frontend_mobile/features/catalogo/producto_model.dart';
import 'package:pos_frontend_mobile/features/mis_compras/compra_model.dart';
import 'package:pos_frontend_mobile/features/mis_facturas/factura_model.dart';
import 'package:pos_frontend_mobile/features/mis_pedidos/pedido_model.dart';
import 'package:pos_frontend_mobile/features/profile/models/credit_model.dart';

Producto _producto(int id, {int empresa = 1, int sucursal = 10, double precio = 5}) => Producto(
      idProducto: id,
      idEmpresa: empresa,
      nombre: 'P$id',
      precio: precio,
      disponible: true,
      idSucursal: sucursal,
    );

void main() {
  group('Producto', () {
    test('lee disponible e id_sucursal del catálogo del cliente', () {
      final p = Producto.fromJson({
        'id_producto': 7,
        'id_empresa': 2,
        'nombre': 'Agua',
        'precio': '3.50',
        'disponible': false,
        'id_sucursal': 4,
      });
      expect(p.precio, 3.5);
      expect(p.disponible, isFalse);
      expect(p.idSucursal, 4);
      expect(formatoBs(p.precio), 'Bs 3.50');
    });
  });

  group('CarritoController', () {
    final carrito = CarritoController.instance;
    setUp(carrito.vaciar);

    test('suma cantidades, subtotales y total', () {
      carrito.agregar(_producto(1, precio: 2));
      carrito.agregar(_producto(1, precio: 2));
      carrito.agregar(_producto(2, precio: 10));
      expect(carrito.cantidadTotal, 3);
      expect(carrito.total, 14);
      carrito.cambiarCantidad(1, -2);
      expect(carrito.items.map((i) => i.producto.idProducto), [2]);
    });

    test('no mezcla empresas ni sucursales en un pedido', () {
      expect(carrito.agregar(_producto(1)), isTrue);
      expect(carrito.agregar(_producto(2, empresa: 2)), isFalse);
      expect(carrito.agregar(_producto(3, sucursal: 99)), isFalse);
      expect(carrito.cantidadTotal, 1);
    });
  });

  test('Compra traduce estados y forma de pago', () {
    Compra compra(String estado, List pagos) => Compra.fromJson({
          'id_venta': 1,
          'estado': estado,
          'total': 10,
          'pagos': pagos,
          'detalles': [
            {'cantidad': 2, 'precio_unitario': 5, 'total': 10, 'producto': {'nombre': 'Pan'}},
          ],
        });
    expect(compra('PAGADA', []).estadoTexto, 'PAGADO');
    expect(compra('CREDITO', []).estadoTexto, 'CRÉDITO');
    expect(compra('CREDITO', []).formaPago, 'Crédito');
    expect(compra('PENDIENTE_COBRO', []).estadoTexto, 'PENDIENTE');
    final pagada = compra('PAGADA', [
      {'monto': 10, 'metodo_pago': {'nombre': 'QR'}},
    ]);
    expect(pagada.formaPago, 'QR');
    expect(pagada.detalles.single.producto, 'Pan');
  });

  test('Factura traduce estado SIAT y lee el QR', () {
    final f = Factura.fromJson({
      'id_factura': 3,
      'numero_factura': 42,
      'monto_total': 113,
      'iva': 13,
      'estado_siat': 'VALIDADA',
      'qr_code_data': 'https://siat.example/qr',
    });
    expect(f.estadoTexto, 'VÁLIDA');
    expect(f.numeroTexto, 'N° 42');
    expect(f.qrCodeData, isNotNull);
    expect(Factura.fromJson({'id_factura': 4, 'estado_siat': 'ANULADA'}).estadoTexto, 'ANULADA');
  });

  test('Crédito: los pagos informados pendientes descuentan el saldo por informar', () {
    final cxc = AccountReceivable.fromJson({
      'id_cxc': 9,
      'monto_credito': 100,
      'saldo_pendiente': 80,
      'venta': {'id_venta': 1},
      'pagos_reportados': [
        {'id_pago_reportado': 1, 'monto': 30, 'estado': 'PENDIENTE'},
        {'id_pago_reportado': 2, 'monto': 20, 'estado': 'CONFIRMADO'},
      ],
    });
    expect(cxc.montoInformadoPendiente, 30);
    expect(cxc.saldoPorInformar, 50);
    final otro = cxc.conPagoReportado(
      const ReportedPayment(idPagoReportado: 3, monto: 50, estado: 'PENDIENTE'),
    );
    expect(otro.saldoPendiente, 80, reason: 'el saldo real solo baja cuando la empresa confirma');
    expect(otro.saldoPorInformar, 0);
  });

  group('Confirmación del cliente (móvil)', () {
    final carrito = CarritoController.instance;
    setUp(carrito.vaciar);

    test('Confirmar pedido no arranca si el carrito está vacío', () async {
      expect(carrito.vacio, isTrue);
      expect(await carrito.confirmar(), isNull);
      expect(carrito.enviando, isFalse);
    });

    test('payload de detalles lista productos listos para POST /api/pedidos', () {
      carrito.agregar(_producto(11, precio: 7.5));
      carrito.agregar(_producto(11, precio: 7.5));
      carrito.agregar(_producto(22, precio: 3));
      final detalles = carrito.items
          .map((i) => {
                'id_producto': i.producto.idProducto,
                'cantidad': i.cantidad,
                'precio_unitario_estimado': i.producto.precio,
              })
          .toList();
      expect(detalles, [
        {'id_producto': 11, 'cantidad': 2, 'precio_unitario_estimado': 7.5},
        {'id_producto': 22, 'cantidad': 1, 'precio_unitario_estimado': 3.0},
      ]);
      expect(carrito.idEmpresa, 1);
      expect(carrito.total, 18);
    });

    test('ReportedPayment queda pendiente hasta confirmación de la empresa', () {
      const pendiente = ReportedPayment(idPagoReportado: 1, monto: 40, estado: 'PENDIENTE');
      const confirmado = ReportedPayment(idPagoReportado: 2, monto: 40, estado: 'CONFIRMADO');
      expect(pendiente.pendiente, isTrue);
      expect(confirmado.pendiente, isFalse);
    });

    test('Pedido traduce estados y permite cancelar solo al inicio', () {
      final p = Pedido.fromJson({
        'id_pedido': 5,
        'id_empresa': 1,
        'id_sucursal': 2,
        'estado': 'enviado',
        'total_estimado': 25.5,
        'detalles': [
          {
            'id_detalle': 1,
            'id_producto': 9,
            'cantidad': 2,
            'precio_unitario_estimado': 10,
            'subtotal_estimado': 20,
          },
        ],
      });
      expect(p.estadoTexto, 'Enviado');
      expect(p.puedeCancelar, isTrue);
      expect(p.totalEstimado, 25.5);
      expect(p.detalles.single.productoTexto, 'Producto #9');
      expect(
        Pedido.fromJson({
          'id_pedido': 6,
          'id_empresa': 1,
          'id_sucursal': 2,
          'estado': 'confirmado',
          'total_estimado': 1,
        }).puedeCancelar,
        isFalse,
      );
    });
  });
}
