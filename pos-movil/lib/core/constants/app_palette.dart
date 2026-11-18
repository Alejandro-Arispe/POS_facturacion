import 'package:flutter/material.dart';

/// Tokens del tema claro del frontend Angular (pos-frontend/src/styles.css).
class AppPalette {
  const AppPalette._();

  static const Color bg = Color(0xFFF7F8FA); // --color-bg
  static const Color bgSoft = Color(0xFFF3F5F8); // --color-bg-soft
  static const Color surface = Color(0xFFFFFFFF); // --color-surface
  static const Color surface2 = Color(0xFFF8FAFC); // --color-surface-2

  static const Color primary = Color(0xFF356BDB); // --pos-brand
  static const Color primaryHover = Color(0xFF2758BF); // --pos-brand-hover
  static const Color primarySoft = Color(0xFFEDF3FF); // --pos-brand-soft

  static const Color text = Color(0xFF283342); // --color-text
  static const Color textSoft = Color(0xFF566170); // --color-text-soft

  static const Color border = Color(0xFFE5E8EE); // --color-border
  static const Color success = Color(0xFF07845C); // --color-success
  static const Color danger = Color(0xFFC73842); // --color-danger
  static const Color warning = Color(0xFFC56B17); // --color-warning
  static const Color info = Color(0xFF356BDB); // --color-info

  /// --radius-sm / --radius-md
  static const double radiusSm = 8;
  static const double radiusMd = 12;
}
