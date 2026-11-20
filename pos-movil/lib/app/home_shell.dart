import 'dart:async';

import 'package:flutter/material.dart';

import '../core/services/fcm_global_service.dart';
import '../features/catalogo/catalogo_page.dart';
import '../features/mis_compras/compras_page.dart';
import '../features/mis_facturas/facturas_page.dart';
import '../features/mis_pedidos/pedidos_page.dart';
import '../features/profile/pages/profile_page.dart';

/// Contenedor principal tras el login con la navegación inferior.
class HomeShell extends StatefulWidget {
  final int initialIndex;

  const HomeShell({super.key, this.initialIndex = 0});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  static const int _tabPedidos = 0;
  static const int _tabPerfil = 4;

  late int _index = widget.initialIndex;
  StreamSubscription<Map<String, dynamic>>? _tapSub;

  @override
  void initState() {
    super.initState();
    // App abierta desde una notificación con la app cerrada.
    // Solo se "mira" el tap pendiente; ProfilePage lo consume para créditos.
    final pending = FcmGlobalService.instance.pendingNotificationTap;
    if (pending != null) {
      _index = _tabForNotification(pending);
      if (FcmGlobalService.isPedidoNotification(pending)) {
        FcmGlobalService.instance.clearPendingNotificationTap();
      }
    }
    _tapSub = FcmGlobalService.instance.notificationTaps.listen((data) {
      if (mounted) setState(() => _index = _tabForNotification(data));
    });
  }

  @override
  void dispose() {
    _tapSub?.cancel();
    super.dispose();
  }

  /// Pedidos -> pestaña Pedidos; créditos/abonos -> Perfil (sección Créditos).
  int _tabForNotification(Map<String, dynamic> data) =>
      FcmGlobalService.isPedidoNotification(data) ? _tabPedidos : _tabPerfil;

  @override
  Widget build(BuildContext context) {
    final pages = <Widget>[
      const PedidosPage(),
      const CatalogoPage(),
      const ComprasPage(),
      FacturasPage(active: _index == 3),
      const ProfilePage(),
    ];

    return Scaffold(
      body: IndexedStack(index: _index, children: pages),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (i) => setState(() => _index = i),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.local_shipping_outlined),
            selectedIcon: Icon(Icons.local_shipping),
            label: 'Pedidos',
          ),
          NavigationDestination(
            icon: Icon(Icons.storefront_outlined),
            selectedIcon: Icon(Icons.storefront),
            label: 'Catálogo',
          ),
          NavigationDestination(
            icon: Icon(Icons.receipt_long_outlined),
            selectedIcon: Icon(Icons.receipt_long),
            label: 'Mis Compras',
          ),
          NavigationDestination(
            icon: Icon(Icons.description_outlined),
            selectedIcon: Icon(Icons.description),
            label: 'Mis Facturas',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person),
            label: 'Perfil',
          ),
        ],
      ),
    );
  }
}
