import 'package:flutter/material.dart';

import '../../core/constants/app_palette.dart';

/// Contenido provisional para pantallas que aún no tienen lógica.
class PlaceholderView extends StatelessWidget {
  final IconData icon;
  final String title;

  const PlaceholderView({super.key, required this.icon, required this.title});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(icon, size: 40, color: AppPalette.primary),
                const SizedBox(height: 12),
                Text(
                  title,
                  style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 4),
                const Text(
                  'Próximamente',
                  style: TextStyle(color: AppPalette.textSoft),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
