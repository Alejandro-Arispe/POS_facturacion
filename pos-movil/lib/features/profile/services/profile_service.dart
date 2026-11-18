import 'package:flutter/foundation.dart';
import '../../../core/api_service.dart';
import '../../../core/storage/token_storage.dart';
import '../models/company_model.dart';
import '../models/client_profile_model.dart';
import '../models/client_category_model.dart';
import '../models/client_company_detail_model.dart';
import '../models/credit_model.dart';
import '../models/notification_model.dart';

class ProfileService {
  final ApiService _apiService;
  final TokenStorage _tokenStorage;

  ProfileService({
    ApiService? apiService,
    TokenStorage? tokenStorage,
  })  : _apiService = apiService ?? ApiService(),
        _tokenStorage = tokenStorage ?? TokenStorage();

  TokenStorage get tokenStorage => _tokenStorage;

  Future<List<Company>> getMyCompanies() async {
    final response = await _apiService.get('/api/empresas/mis-empresas');
    if (response is List) {
      return response.map((json) => Company.fromJson(json as Map<String, dynamic>)).toList();
    }
    return [];
  }

  Future<List<ClientProfile>> getMyClientProfiles(int userId) async {
    final response = await _apiService.get('/api/clientes/$userId');
    if (response is List) {
      return response.map((json) => ClientProfile.fromJson(json as Map<String, dynamic>)).toList();
    }
    return [];
  }

  Future<ClientCategory> getCategory(int companyId, int categoryId) async {
    final response = await _apiService.get('/api/categorias-cliente/$companyId/$categoryId');
    if (response is Map<String, dynamic>) {
      return ClientCategory.fromJson(response);
    }
    throw const ApiException('Respuesta de categoría inválida del servidor');
  }

  Future<List<ClientCategory>> getCompanyCategories(int companyId) async {
    final response = await _apiService.get('/api/categorias-cliente/$companyId');
    if (response is List) {
      return response.map((json) => ClientCategory.fromJson(json as Map<String, dynamic>)).toList();
    }
    return [];
  }

  Future<ClientCompanyDetail?> getClientDetail(int companyId, int userId) async {
    final response = await _apiService.get('/api/empresas/$companyId/clientes');
    if (response is List) {
      final list = response.map((json) => ClientCompanyDetail.fromJson(json as Map<String, dynamic>)).toList();
      for (final detail in list) {
        if (detail.idUsuario == userId) {
          return detail;
        }
      }
    }
    return null;
  }

  Future<List<AccountReceivable>> getClientCredits(int companyId, int clientId) async {
    final response = await _apiService.get('/api/empresas/$companyId/clientes/$clientId/cuentas-por-cobrar');
    if (response is List) {
      return response.map((json) => AccountReceivable.fromJson(json as Map<String, dynamic>)).toList();
    }
    return [];
  }

  Future<List<NotificationModel>> getNotifications(int companyId, int userId) async {
    final response = await _apiService.get('/notifications/history/empresas/$companyId?id_usuario=$userId');
    if (response is Map<String, dynamic> && response['items'] is List) {
      final items = response['items'] as List;
      final list = items.map((json) => NotificationModel.fromJson(json as Map<String, dynamic>)).toList();
      
      // Client-side safety filter: keep only user-specific alerts
      final filteredList = list.where((n) {
        final payload = n.payload;
        if (payload != null && payload.containsKey('id_usuario')) {
          final notifUserId = int.tryParse(payload['id_usuario'].toString());
          return notifUserId == userId;
        }
        return false;
      }).toList();

      // Sort newest first
      filteredList.sort((a, b) => b.fecha.compareTo(a.fecha));
      return filteredList;
    }
    return [];
  }

  Future<bool> markNotificationAsRead(int notificationId) async {
    try {
      final response = await _apiService.post('/notifications/mark-read', {'id': notificationId});
      if (response is Map<String, dynamic> && response['ok'] == true) {
        return true;
      }
    } catch (_) {}
    return false;
  }

  Future<bool> registerDeviceToken(String token, int userId, int companyId) async {
    final String tokenParcial = token.length > 20 ? token.substring(0, 20) : token;
    debugPrint('[FCM REGISTER] Enviando token a backend: uid=$userId empresa=$companyId token=$tokenParcial...');
    try {
      final response = await _apiService.post('/notifications/register-token', {
        'token': token,
        'uid_usuario': userId.toString(),
        'rol': 'CLIENTE',
        'plataforma': 'android',
        'id_empresa': companyId,
      });
      debugPrint('[FCM REGISTER] Respuesta del backend: $response');
      if (response is Map<String, dynamic> && response['ok'] == true) {
        debugPrint('[FCM REGISTER] Token registrado exitosamente para empresa=$companyId');
        return true;
      }
      debugPrint('[FCM REGISTER] Respuesta inesperada del backend: $response');
    } catch (e, st) {
      debugPrint('[FCM REGISTER] ERROR al registrar token: $e');
      debugPrint('[FCM REGISTER] StackTrace: $st');
    }
    return false;
  }
}

