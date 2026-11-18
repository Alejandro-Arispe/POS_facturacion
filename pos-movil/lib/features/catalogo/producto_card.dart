import 'package:flutter/material.dart';

import '../../core/constants/app_palette.dart';
import 'producto_model.dart';

class ProductoCard extends StatelessWidget {
  final Producto producto;
  final VoidCallback onAgregar;

  const ProductoCard({super.key, required this.producto, required this.onAgregar});

  @override
  Widget build(BuildContext context) {
    final disponible = producto.disponible;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Text(
                    producto.nombre,
                    style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                  ),
                ),
                const SizedBox(width: 8),
                _EstadoChip(disponible: disponible),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              formatoBs(producto.precio),
              style: const TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w600,
                color: AppPalette.primary,
              ),
            ),
            if (disponible) ...[
              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: onAgregar,
                icon: const Icon(Icons.add_shopping_cart, size: 20),
                label: const Text('Agregar al carrito'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _EstadoChip extends StatelessWidget {
  final bool disponible;

  const _EstadoChip({required this.disponible});

  @override
  Widget build(BuildContext context) {
    final color = disponible ? AppPalette.success : AppPalette.danger;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        disponible ? 'Disponible' : 'Agotado',
        style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w700),
      ),
    );
  }
}
