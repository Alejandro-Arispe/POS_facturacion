import 'package:dio/dio.dart';

import 'constants/api_constants.dart';
import 'storage/token_storage.dart';

class ApiException implements Exception {
  final String message;

  const ApiException(this.message);

  @override
  String toString() => message;
}

class ApiService {
  late final Dio _dio;
  final TokenStorage _tokenStorage = TokenStorage();

  ApiService() {
    _dio = Dio(
      BaseOptions(
        baseUrl: ApiConstants.baseUrl,
        contentType: Headers.jsonContentType,
        responseType: ResponseType.json,
        connectTimeout: const Duration(seconds: 20),
        receiveTimeout: const Duration(seconds: 20),
      ),
    );

    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          final token = await _tokenStorage.getToken();
          if (token != null && token.isNotEmpty) {
            options.headers['Authorization'] = 'Bearer $token';
          }
          return handler.next(options);
        },
        onError: (error, handler) {
          return handler.next(error);
        },
      ),
    );
  }

  Future<dynamic> get(String endpoint) async {
    try {
      final response = await _dio.get(endpoint);
      return response.data;
    } on DioException catch (error) {
      throw _handleError(error);
    }
  }

  Future<dynamic> post(String endpoint, dynamic body) async {
    try {
      final response = await _dio.post(endpoint, data: body);
      return response.data;
    } on DioException catch (error) {
      throw _handleError(error);
    }
  }

  Future<dynamic> postFormData(String endpoint, FormData formData) async {
    try {
      final response = await _dio.post(
        endpoint,
        data: formData,
        options: Options(contentType: Headers.multipartFormDataContentType),
      );
      return response.data;
    } on DioException catch (error) {
      throw _handleError(error);
    }
  }

  Future<dynamic> put(String endpoint, Map<String, dynamic> body) async {
    try {
      final response = await _dio.put(endpoint, data: body);
      return response.data;
    } on DioException catch (error) {
      throw _handleError(error);
    }
  }

  Future<dynamic> patch(String endpoint, dynamic body) async {
    try {
      final response = await _dio.patch(endpoint, data: body);
      return response.data;
    } on DioException catch (error) {
      throw _handleError(error);
    }
  }

  Future<dynamic> delete(String endpoint) async {
    try {
      final response = await _dio.delete(endpoint);
      return response.data;
    } on DioException catch (error) {
      throw _handleError(error);
    }
  }

  ApiException _handleError(DioException error) {
    String message = 'Error en la solicitud';

    if (error.response != null) {
      final data = error.response?.data;
      if (data is Map && data['detail'] != null) {
        message = data['detail'].toString();
      } else if (data is Map && data['message'] != null) {
        message = data['message'].toString();
      } else {
        message = 'Error ${error.response?.statusCode}';
      }
    } else if (error.type == DioExceptionType.connectionTimeout) {
      message = 'Tiempo de conexion agotado';
    } else if (error.type == DioExceptionType.receiveTimeout) {
      message = 'Tiempo de respuesta agotado';
    } else if (error.type == DioExceptionType.unknown) {
      message = 'Error de conexion';
    }

    return ApiException(message);
  }
}
