class ApiConstants {
  /// Por USB: `adb reverse tcp:8000 tcp:8000` y este host apunta al backend local.
  /// Remoto: pasar `--dart-define=API_BASE_URL=https://pos-backend-app.duckdns.org`
  /// LAN WiFi: `--dart-define=API_BASE_URL=http://TU_IP:8000`
  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://127.0.0.1:8000',
  );

  static const String login = '/api/auth/login';
  static const String register = '/api/auth/register';
  static const String verifyCode = '/api/auth/verify-code';
}
