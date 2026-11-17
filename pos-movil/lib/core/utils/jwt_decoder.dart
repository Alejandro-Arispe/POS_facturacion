import 'dart:convert';

class JwtDecoder {
  const JwtDecoder._();

  static Map<String, dynamic> decode(String token) {
    try {
      final parts = token.split('.');
      if (parts.length != 3) {
        throw const FormatException('Token JWT inválido: no contiene 3 partes.');
      }

      final payloadPart = parts[1];
      final normalized = base64Url.normalize(payloadPart);
      final decodedBytes = base64Url.decode(normalized);
      final jsonString = utf8.decode(decodedBytes);

      return json.decode(jsonString) as Map<String, dynamic>;
    } catch (e) {
      throw FormatException('Error decodificando token: $e');
    }
  }

  static String? getUserId(String token) {
    try {
      final claims = decode(token);
      return claims['sub']?.toString();
    } catch (_) {
      return null;
    }
  }

  static String? getEmail(String token) {
    try {
      final claims = decode(token);
      return claims['email']?.toString();
    } catch (_) {
      return null;
    }
  }
}
