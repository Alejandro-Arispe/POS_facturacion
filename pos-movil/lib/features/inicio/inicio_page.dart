import 'package:flutter/material.dart';

import '../../shared/widgets/placeholder_view.dart';

class InicioPage extends StatelessWidget {
  const InicioPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Inicio')),
      body: const PlaceholderView(icon: Icons.home_outlined, title: 'Inicio'),
    );
  }
}
