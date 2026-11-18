import 'package:flutter/material.dart';

import '../features/auth/pages/login_page.dart';
import '../features/auth/pages/register_page.dart';
import '../features/profile/pages/profile_page.dart';
import '../features/profile/pages/credits_page.dart';
import '../features/profile/pages/credit_detail_page.dart';
import '../features/profile/models/credit_model.dart';
import '../features/carrito/carrito_page.dart';
import '../features/creditos/pago_credito_page.dart';
import '../features/mis_compras/compra_detalle_page.dart';
import '../features/mis_compras/compra_model.dart';
import '../features/mis_facturas/factura_detalle_page.dart';
import '../features/mis_facturas/factura_model.dart';
import 'home_shell.dart';

class AppRouter {
  const AppRouter._();

  static const String login = '/login';
  static const String register = '/register';
  static const String profile = '/profile';
  static const String credits = '/credits';
  static const String creditDetail = '/credit-detail';
  static const String home = '/home';
  static const String carrito = '/carrito';
  static const String compraDetalle = '/compra-detalle';
  static const String facturaDetalle = '/factura-detalle';
  static const String pagoCredito = '/pago-credito';

  static Route<dynamic> generateRoute(RouteSettings settings) {
    switch (settings.name) {
      case home:
        return MaterialPageRoute(
          builder: (_) => HomeShell(initialIndex: settings.arguments as int? ?? 0),
        );
      case carrito:
        return MaterialPageRoute(builder: (_) => const CarritoPage());
      case compraDetalle:
        return MaterialPageRoute(
          builder: (_) => CompraDetallePage(compra: settings.arguments as Compra),
        );
      case facturaDetalle:
        return MaterialPageRoute(
          builder: (_) => FacturaDetallePage(factura: settings.arguments as Factura),
        );
      case pagoCredito:
        return MaterialPageRoute(
          builder: (_) => PagoCreditoPage(credit: settings.arguments as AccountReceivable),
        );
      case login:
        return MaterialPageRoute(builder: (_) => const LoginPage());
      case register:
        return MaterialPageRoute(builder: (_) => const RegisterPage());
      case profile:
        return MaterialPageRoute(builder: (_) => const ProfilePage());
      case credits:
        final args = settings.arguments as Map<String, dynamic>;
        return MaterialPageRoute(
          builder: (_) => CreditsPage(
            companyId: args['companyId'] as int,
            companyName: args['companyName'] as String,
            clientId: args['clientId'] as int,
          ),
        );
      case creditDetail:
        final credit = settings.arguments as AccountReceivable;
        return MaterialPageRoute(
          builder: (_) => CreditDetailPage(credit: credit),
        );
      default:
        return MaterialPageRoute(
          builder: (_) => _UnknownRoutePage(routeName: settings.name),
        );
    }
  }
}

class _UnknownRoutePage extends StatelessWidget {
  final String? routeName;

  const _UnknownRoutePage({required this.routeName});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Text('Ruta no encontrada: ${routeName ?? ''}'),
      ),
    );
  }
}
