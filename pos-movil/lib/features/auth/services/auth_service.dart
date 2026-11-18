import '../../../core/api_service.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/storage/token_storage.dart';
import '../models/login_response.dart';
import '../models/register_response.dart';
import '../models/verify_code_response.dart';

class AuthService {
  final ApiService _apiService;
  final TokenStorage _tokenStorage;

  AuthService({
    ApiService? apiService,
    TokenStorage? tokenStorage,
  })  : _apiService = apiService ?? ApiService(),
        _tokenStorage = tokenStorage ?? TokenStorage();

  Future<LoginResponse> login({
    required String email,
    required String contrasena,
  }) async {
    final data = await _apiService.post(
      ApiConstants.login,
      {
        'email': email,
        'contrasena': contrasena,
      },
    );

    if (data is! Map<String, dynamic>) {
      throw const ApiException('Respuesta invalida del servidor');
    }

    final loginResponse = LoginResponse.fromJson(data);
    if (loginResponse.accessToken.isEmpty) {
      throw const ApiException('El servidor no devolvio un token');
    }

    await _tokenStorage.saveToken(
      loginResponse.accessToken,
      tokenType: loginResponse.tokenType,
    );

    return loginResponse;
  }

  Future<RegisterResponse> register({
    required String email,
    required String contrasena,
    required String nombreCompleto,
    required String fechaNacimiento,
    required String genero,
    required String telefono,
    required String documento,
  }) async {
    final data = await _apiService.post(
      ApiConstants.register,
      {
        'email': email,
        'contrasena': contrasena,
        'nombre_completo': nombreCompleto,
        'fecha_nacimiento': fechaNacimiento,
        'genero': genero,
        'telefono': telefono,
        'documento': documento,
      },
    );

    if (data is! Map<String, dynamic>) {
      throw const ApiException('Respuesta invalida del servidor');
    }

    return RegisterResponse.fromJson(data);
  }

  Future<VerifyCodeResponse> verifyCode({
    required String email,
    required String codigo,
  }) async {
    final data = await _apiService.post(
      ApiConstants.verifyCode,
      {
        'email': email,
        'codigo': codigo,
      },
    );

    if (data is! Map<String, dynamic>) {
      throw const ApiException('Respuesta invalida del servidor');
    }

    return VerifyCodeResponse.fromJson(data);
  }
}
