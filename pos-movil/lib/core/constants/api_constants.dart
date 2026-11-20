import 'dart:io' show Platform;
import 'package:flutter/foundation.dart' show kIsWeb;

class ApiConstants {
  static const String _envUrl = String.fromEnvironment('API_BASE_URL');

  /// IP de tu laptop en la red Wi-Fi local para conectar desde tu celular físico.
  /// Si usas el emulador de Android Studio en la PC, puedes cambiar a '10.0.2.2'.
  static const String hostIp = '192.168.0.4';

  static String get baseUrl {
    if (_envUrl.isNotEmpty) return _envUrl;
    if (!kIsWeb && Platform.isAndroid) {
      return 'http://$hostIp:8000';
    }
    return 'http://127.0.0.1:8000';
  }

  static const String login = '/api/auth/login';
  static const String register = '/api/auth/register';
  static const String verifyCode = '/api/auth/verify-code';
}
