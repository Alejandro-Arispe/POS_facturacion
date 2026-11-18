import 'package:flutter/material.dart';

import '../../core/constants/app_palette.dart';

/// dd/MM/yyyy HH:mm (sin depender de intl).
String formatoFecha(DateTime? fecha) {
  if (fecha == null) return '—';
  String dos(int n) => n.toString().padLeft(2, '0');
  return '${dos(fecha.day)}/${dos(fecha.month)}/${fecha.year} ${dos(fecha.hour)}:${dos(fecha.minute)}';
}

double aDouble(dynamic valor) => double.tryParse('${valor ?? ''}') ?? 0;

/// Etiqueta de estado tipo "pill".
class EstadoChip extends StatelessWidget {
  final String texto;
  final Color color;

  const EstadoChip({super.key, required this.texto, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        texto,
        style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w700),
      ),
    );
  }
}

/// Estado vacío o de error para listas, con opción de reintentar.
class EstadoListaView extends StatelessWidget {
  final IconData icon;
  final String titulo;
  final String mensaje;
  final VoidCallback? onReintentar;

  const EstadoListaView({
    super.key,
    required this.icon,
    required this.titulo,
    required this.mensaje,
    this.onReintentar,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 64, 24, 24),
      child: Column(
        children: [
          Icon(icon, size: 48, color: AppPalette.textSoft),
          const SizedBox(height: 12),
          Text(titulo,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          Text(mensaje,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppPalette.textSoft)),
          if (onReintentar != null) ...[
            const SizedBox(height: 16),
            OutlinedButton.icon(
              onPressed: onReintentar,
              icon: const Icon(Icons.refresh),
              label: const Text('Reintentar'),
            ),
          ],
        ],
      ),
    );
  }
}

/// Fila etiqueta / valor para pantallas de detalle.
class DatoFila extends StatelessWidget {
  final String etiqueta;
  final String valor;
  final bool destacado;

  const DatoFila(this.etiqueta, this.valor, {super.key, this.destacado = false});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            flex: 2,
            child: Text(etiqueta, style: const TextStyle(color: AppPalette.textSoft)),
          ),
          Expanded(
            flex: 3,
            child: Text(
              valor,
              textAlign: TextAlign.right,
              style: TextStyle(
                fontWeight: destacado ? FontWeight.w800 : FontWeight.w600,
                fontSize: destacado ? 16 : 14,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
