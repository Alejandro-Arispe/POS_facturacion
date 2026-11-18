import 'package:shared_preferences/shared_preferences.dart';

class TokenStorage {
  static const String _tokenKey = 'access_token';
  static const String _tokenTypeKey = 'token_type';

  Future<void> saveToken(String token, {String tokenType = 'bearer'}) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString(_tokenKey, token);
    await preferences.setString(_tokenTypeKey, tokenType);
  }

  Future<String?> getToken() async {
    final preferences = await SharedPreferences.getInstance();
    return preferences.getString(_tokenKey);
  }

  Future<String?> getTokenType() async {
    final preferences = await SharedPreferences.getInstance();
    return preferences.getString(_tokenTypeKey);
  }

  Future<void> clearToken() async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.remove(_tokenKey);
    await preferences.remove(_tokenTypeKey);
  }

  Future<bool> hasToken() async {
    final token = await getToken();
    return token != null && token.isNotEmpty;
  }
}
