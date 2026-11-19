import 'dart:io' show Platform;
import 'package:flutter/foundation.dart' show kIsWeb;

class ApiConstants {
  static const String _envUrl = String.fromEnvironment('API_BASE_URL');

  /// En emulador Android, 127.0.0.1 apunta al propio emulador.
  /// Para conectar con el backend de la PC host se debe usar 10.0.2.2.
  /// En celular físico por USB: usar `adb reverse tcp:8000 tcp:8000` o IP local de la PC.
  static String get baseUrl {
    if (_envUrl.isNotEmpty) return _envUrl;
    if (!kIsWeb && Platform.isAndroid) {
      return 'http://10.0.2.2:8000';
    }
    return 'http://127.0.0.1:8000';
  }

  static const String login = '/api/auth/login';
  static const String register = '/api/auth/register';
  static const String verifyCode = '/api/auth/verify-code';
}
