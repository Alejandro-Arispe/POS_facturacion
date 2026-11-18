import 'package:flutter/foundation.dart';

import '../../core/api_service.dart';
import '../profile/models/company_model.dart';
import '../profile/services/profile_service.dart';
import 'producto_model.dart';

/// Carga las empresas del cliente y el catálogo de la empresa seleccionada.
class CatalogoController extends ChangeNotifier {
  final ApiService _api;
  final ProfileService _profileService;

  CatalogoController({ApiService? api, ProfileService? profileService})
      : _api = api ?? ApiService(),
        _profileService = profileService ?? ProfileService();

  List<Company> empresas = [];
  Company? empresa;
  List<Producto> productos = [];
  bool cargando = false;
  String? error;

  /// Carga las empresas y el catálogo de la primera. Lanza [ApiException] si falla.
  Future<void> cargar() async {
    _inicio();
    try {
      empresas = await _profileService.getMyCompanies();
      empresa = empresas.isEmpty ? null : empresas.first;
      await _cargarProductos();
    } on ApiException catch (e) {
      error = e.message;
      rethrow;
    } finally {
      cargando = false;
      notifyListeners();
    }
  }

  Future<void> seleccionarEmpresa(Company nueva) async {
    empresa = nueva;
    _inicio();
    try {
      await _cargarProductos();
    } on ApiException catch (e) {
      error = e.message;
      rethrow;
    } finally {
      cargando = false;
      notifyListeners();
    }
  }

  Future<void> recargar() => empresa == null ? cargar() : seleccionarEmpresa(empresa!);

  void _inicio() {
    cargando = true;
    error = null;
    notifyListeners();
  }

  Future<void> _cargarProductos() async {
    productos = [];
    if (empresa == null) return;
    final data = await _api.get('/api/empresas/${empresa!.idEmpresa}/productos');
    if (data is List) {
      productos = data
          .map((json) => Producto.fromJson(json as Map<String, dynamic>))
          .toList();
    }
  }
}
