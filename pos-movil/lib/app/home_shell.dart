import 'package:flutter/material.dart';

import '../features/catalogo/catalogo_page.dart';
import '../features/inicio/inicio_page.dart';
import '../features/mis_compras/compras_page.dart';
import '../features/mis_facturas/facturas_page.dart';
import '../features/profile/pages/profile_page.dart';

/// Contenedor principal tras el login con la navegación inferior.
class HomeShell extends StatefulWidget {
  final int initialIndex;

  const HomeShell({super.key, this.initialIndex = 0});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  late int _index = widget.initialIndex;

  static const _pages = <Widget>[
    InicioPage(),
    CatalogoPage(),
    ComprasPage(),
    FacturasPage(),
    ProfilePage(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(index: _index, children: _pages),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (i) => setState(() => _index = i),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home),
            label: 'Inicio',
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
