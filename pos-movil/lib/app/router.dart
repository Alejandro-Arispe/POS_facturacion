import 'package:flutter/material.dart';

import '../features/auth/pages/login_page.dart';
import '../features/auth/pages/register_page.dart';
import '../features/profile/pages/profile_page.dart';
import '../features/profile/pages/credits_page.dart';
import '../features/profile/pages/credit_detail_page.dart';
import '../features/profile/models/credit_model.dart';

class AppRouter {
  const AppRouter._();

  static const String login = '/login';
  static const String register = '/register';
  static const String profile = '/profile';
  static const String credits = '/credits';
  static const String creditDetail = '/credit-detail';

  static Route<dynamic> generateRoute(RouteSettings settings) {
    switch (settings.name) {
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
