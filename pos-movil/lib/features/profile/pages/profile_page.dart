import 'dart:async';

import 'package:flutter/material.dart';
import 'package:firebase_messaging/firebase_messaging.dart';

import '../../../app/router.dart';
import '../../../core/constants/app_palette.dart';
import '../../../core/services/fcm_global_service.dart';
import '../../../core/utils/jwt_decoder.dart';
import '../../../shared/widgets/side_menu_scaffold.dart';
import '../models/client_category_model.dart';
import '../models/client_company_detail_model.dart';
import '../models/client_profile_model.dart';
import '../models/company_model.dart';
import '../models/notification_model.dart';
import '../services/profile_service.dart';

class ProfilePage extends StatefulWidget {
  const ProfilePage({super.key});

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  final _profileService = ProfileService();
  bool _isLoading = true;
  String? _errorMessage;

  // FCM stream subscriptions gestionadas por este widget
  // (onMessage y onMessageOpenedApp viven en FcmGlobalService)
  StreamSubscription<Map<String, dynamic>>? _onNotificationTapSub;
  StreamSubscription<String>? _onTokenRefreshSub;
  bool _firebaseMessagingInitialized = false;

  // Logged in user info
  int? _userId;
  String? _userEmail;

  // Loaded data
  List<Company> _companies = [];
  List<ClientProfile> _clientProfiles = [];
  List<NotificationModel> _notifications = [];

  // Selected company and corresponding profiles
  Company? _selectedCompany;
  ClientProfile? _selectedProfile;
  ClientCategory? _selectedCategory;
  ClientCompanyDetail? _companyDetail;

  // Financial fields
  double _utilizedCredit = 0.0;
  double _limitCredit = 0.0;
  double _availableCredit = 0.0;

  // Navigation menu state
  int _currentSectionIndex = 0; // 0 = Home, 1 = Credits, 2 = Notifications

  @override
  void initState() {
    super.initState();
    _loadInitialData();
  }

  Future<void> _loadInitialData() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final token = await _profileService.tokenStorage.getToken();
      if (token == null || token.isEmpty) {
        if (!mounted) return;
        Navigator.of(context).pushReplacementNamed(AppRouter.login);
        return;
      }

      final userIdStr = JwtDecoder.getUserId(token);
      final email = JwtDecoder.getEmail(token);

      if (userIdStr == null) {
        throw Exception('Token inválido: No se pudo extraer el ID de usuario.');
      }

      _userId = int.parse(userIdStr);
      _userEmail = email;

      // Fetch companies and client profiles in parallel FIRST
      final results = await Future.wait([
        _profileService.getMyCompanies(),
        _profileService.getMyClientProfiles(_userId!),
      ]);

      _companies = results[0] as List<Company>;
      _clientProfiles = results[1] as List<ClientProfile>;

      if (_companies.isNotEmpty && _clientProfiles.isNotEmpty) {
        _selectedCompany = _companies.first;

        // Initialize Firebase Messaging AFTER companies are loaded
        // so onTokenRefresh can register tokens per company immediately
        await _initFirebaseMessaging();

        // Get real Firebase Cloud Messaging token
        String? fcmToken;
        try {
          fcmToken = await FirebaseMessaging.instance.getToken();
          debugPrint('[FCM] Token obtenido: $fcmToken');
        } catch (e) {
          debugPrint('[FCM] Error al obtener token FCM: $e');
        }

        // Register token for all companies only if real
        if (fcmToken != null && fcmToken.isNotEmpty) {
          await _registerFcmTokenForCompanies(fcmToken);
        } else {
          debugPrint('[FCM] No se obtuvo token FCM real, no se registra dispositivo.');
        }

        await _loadSelectedCompanyDetails();
      } else {
        setState(() {
          _isLoading = false;
        });
      }
    } catch (e) {
      setState(() {
        _errorMessage = e.toString().replaceAll('Exception: ', '');
        _isLoading = false;
      });
    }
  }

  @override
  void dispose() {
    _onNotificationTapSub?.cancel();
    _onTokenRefreshSub?.cancel();
    super.dispose();
  }

  Future<void> _registerFcmTokenForCompanies(String token) async {
    if (_userId == null) {
      debugPrint('[FCM] register device token abortado: userId es nulo.');
      return;
    }
    if (_companies.isEmpty) {
      debugPrint('[FCM] register device token abortado: la lista de empresas está vacía.');
      return;
    }
    if (token.isEmpty) {
      debugPrint('[FCM] register device token abortado: token es vacío.');
      return;
    }

    final String tokenParcial = token.length > 20 ? token.substring(0, 20) : token;
    debugPrint('[FCM] Iniciando registro de token para $_userId con token parcial: $tokenParcial...');
    
    for (final company in _companies) {
      final success = await _profileService.registerDeviceToken(
        token,
        _userId!,
        company.idEmpresa,
      );
      debugPrint('[FCM] Registro en backend para idEmpresa: ${company.idEmpresa} -> ${success ? "EXITOSO" : "FALLIDO"}');
    }
  }

  Future<void> _initFirebaseMessaging() async {
    final messaging = FirebaseMessaging.instance;

    // 1. Solicitar permiso (Android 13+, iOS)
    try {
      final NotificationSettings settings = await messaging.requestPermission(
        alert: true,
        badge: true,
        sound: true,
      );
      debugPrint('[FCM] Estado del permiso: ${settings.authorizationStatus}');
      if (settings.authorizationStatus == AuthorizationStatus.denied) {
        debugPrint('[FCM] El usuario RECHAZÓ los permisos de notificación. No se recibirán pushes.');
        return;
      }
    } catch (e) {
      debugPrint('[FCM] Error al solicitar permisos: $e');
    }

    // 2. Token refresh — re-registrar en backend si el token cambia
    _onTokenRefreshSub?.cancel();
    _onTokenRefreshSub = FirebaseMessaging.instance.onTokenRefresh.listen((newToken) {
      debugPrint('[FCM] Token refrescado: ${newToken.substring(0, 20)}...');
      if (_userId != null && _companies.isNotEmpty) {
        _registerFcmTokenForCompanies(newToken);
      }
    });

    // 3. Procesar notificación de apertura pendiente si existe (estado terminado)
    final pendingTap = FcmGlobalService.instance.pendingNotificationTap;
    if (pendingTap != null) {
      debugPrint('[FCM] Procesando notificación pendiente (terminated): $pendingTap');
      FcmGlobalService.instance.clearPendingNotificationTap();
      _handleNotificationTapData(pendingTap);
    }

    // 4. Suscribirse a los taps de notificación globales (foreground/background)
    _onNotificationTapSub?.cancel();
    _onNotificationTapSub = FcmGlobalService.instance.notificationTaps.listen((data) {
      debugPrint('[FCM] Notificación presionada recibida en ProfilePage: $data');
      _handleNotificationTapData(data);
    });

    _firebaseMessagingInitialized = true;
    debugPrint('[FCM] _initFirebaseMessaging completado en ProfilePage.');
  }

  void _handleNotificationTapData(Map<String, dynamic> data) {
    final companyIdStr = data['id_empresa'];
    final companyId = companyIdStr != null ? int.tryParse(companyIdStr.toString()) : null;
    _goToCreditsForCompany(companyId);
  }

  Future<void> _goToCreditsForCompany(int? companyId) async {
    if (!mounted) return;
    if (companyId != null) {
      final matched = _companies.firstWhere(
        (c) => c.idEmpresa == companyId,
        orElse: () => _selectedCompany ?? _companies.first,
      );
      if (matched.idEmpresa != 0 && matched != _selectedCompany) {
        setState(() {
          _selectedCompany = matched;
          _currentSectionIndex = 1; // Ir al apartado de Créditos
        });
        await _loadSelectedCompanyDetails();
        return;
      }
    }
    setState(() {
      _currentSectionIndex = 1; // Ir al apartado de Créditos
    });
  }

  Future<void> _loadSelectedCompanyDetails() async {
    if (_selectedCompany == null || _userId == null) return;

    try {
      setState(() {
        _isLoading = true;
      });

      // 1. Fetch company category list to match which profile belongs to this company
      final categories = await _profileService.getCompanyCategories(_selectedCompany!.idEmpresa);

      ClientProfile? matchedProfile;
      ClientCategory? matchedCategory;

      for (final profile in _clientProfiles) {
        final category = categories.firstWhere(
          (c) => c.idCategoriaCliente == profile.idCategoriaCliente,
          orElse: () => const ClientCategory(
            idCategoriaCliente: 0,
            idEmpresa: 0,
            nombre: '',
            plazoCredito: 0,
            descuentoBase: 0,
            limiteCredito: 0,
            activo: false,
          ),
        );
        if (category.idCategoriaCliente != 0) {
          matchedProfile = profile;
          matchedCategory = category;
          break;
        }
      }

      _selectedProfile = matchedProfile;
      _selectedCategory = matchedCategory;

      // 2. Fetch detailed profile info of client within this company (gets persona details)
      _companyDetail = await _profileService.getClientDetail(
        _selectedCompany!.idEmpresa,
        _userId!,
      );

      // Calculate financial values
      if (_selectedProfile != null) {
        // Limit: Use profile override if > 0, otherwise category limit
        _limitCredit = _selectedProfile!.limiteCredito > 0 
            ? _selectedProfile!.limiteCredito 
            : (_selectedCategory?.limiteCredito ?? 0.0);
            
        // Fetch credits to calculate actual utilized credit
        final credits = await _profileService.getClientCredits(
          _selectedCompany!.idEmpresa,
          _selectedProfile!.idCliente,
        );
        
        double pendingSum = 0.0;
        for (final c in credits) {
          if (c.estado != 'PAGADA' && c.estado != 'ANULADA') {
            pendingSum += c.saldoPendiente;
          }
        }
        _utilizedCredit = pendingSum;
        _availableCredit = (_limitCredit - _utilizedCredit) > 0 ? (_limitCredit - _utilizedCredit) : 0.0;
      } else {
        _limitCredit = 0.0;
        _utilizedCredit = 0.0;
        _availableCredit = 0.0;
      }

      // 3. Fetch notifications for all companies in parallel
      final List<Future<List<NotificationModel>>> notificationFutures = _companies.map((company) {
        return _profileService.getNotifications(company.idEmpresa, _userId!);
      }).toList();

      final List<List<NotificationModel>> notificationsLists = await Future.wait(notificationFutures);
      
      // Flatten and sort the notifications by date (newest first)
      final List<NotificationModel> allNotifications = [];
      for (final list in notificationsLists) {
        allNotifications.addAll(list);
      }
      allNotifications.sort((a, b) => b.fecha.compareTo(a.fecha));
      _notifications = allNotifications;

      setState(() {
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _errorMessage = 'Error al cargar detalles de la empresa: $e';
        _isLoading = false;
      });
    }
  }

  Future<void> _onCompanyChanged(Company? newCompany) async {
    if (newCompany == null || newCompany.idEmpresa == _selectedCompany?.idEmpresa) return;

    setState(() {
      _selectedCompany = newCompany;
    });

    await _loadSelectedCompanyDetails();
  }

  Future<void> _logout() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Cerrar sesión'),
        content: const Text('¿Estás seguro de que deseas salir?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancelar'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            style: ElevatedButton.styleFrom(
              backgroundColor: AppPalette.danger,
              foregroundColor: Colors.white,
            ),
            child: const Text('Cerrar sesión'),
          ),
        ],
      ),
    );

    if (confirm == true) {
      await _profileService.tokenStorage.clearToken();
      if (!mounted) return;
      Navigator.of(context).pushReplacementNamed(AppRouter.login);
    }
  }

  Future<void> _markAsRead(NotificationModel notification) async {
    if (notification.leido) return;

    final success = await _profileService.markNotificationAsRead(notification.id);
    if (success && mounted) {
      setState(() {
        final index = _notifications.indexWhere((n) => n.id == notification.id);
        if (index != -1) {
          _notifications[index] = NotificationModel(
            id: notification.id,
            idEmpresa: notification.idEmpresa,
            prioridad: notification.prioridad,
            tipo: notification.tipo,
            titulo: notification.titulo,
            mensaje: notification.mensaje,
            payload: notification.payload,
            leido: true,
            fecha: notification.fecha,
          );
        }
      });
    }
  }

  int get _unreadCount {
    return _notifications.where((n) => !n.leido).length;
  }

  @override
  Widget build(BuildContext context) {
    final persona = _companyDetail?.usuario.persona;
    final String userName = persona?.nombreCompleto ?? 'Cliente POS';
    final String userEmail = _userEmail ?? '';

    String appBarTitle = 'Mi Perfil de Cliente';
    if (_currentSectionIndex == 1) {
      appBarTitle = 'Gestión de Créditos';
    }

    return Scaffold(
      backgroundColor: AppPalette.bg,
      appBar: AppBar(
        title: Text(appBarTitle, style: const TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: Colors.transparent,
        elevation: 0,
        actions: _isLoading || _companies.isEmpty || _selectedProfile == null
            ? null
            : [
                Stack(
                  alignment: Alignment.center,
                  children: [
                    IconButton(
                      icon: const Icon(Icons.notifications_none, size: 28),
                      onPressed: _showNotificationsBottomSheet,
                    ),
                    if (_unreadCount > 0)
                      Positioned(
                        right: 8,
                        top: 8,
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
                          decoration: const BoxDecoration(
                            color: AppPalette.danger,
                            shape: BoxShape.circle,
                          ),
                          constraints: const BoxConstraints(
                            minWidth: 16,
                            minHeight: 16,
                          ),
                          child: Text(
                            _unreadCount.toString(),
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 9,
                              fontWeight: FontWeight.bold,
                            ),
                            textAlign: TextAlign.center,
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(width: 8),
              ],
      ),
      drawer: _isLoading || _companies.isEmpty || _selectedProfile == null
          ? null
          : ClientDrawer(
              userName: userName,
              userEmail: userEmail,
              currentSectionIndex: _currentSectionIndex,
              unreadNotificationsCount: _unreadCount,
              onSectionSelected: (index) {
                setState(() {
                  _currentSectionIndex = index;
                });
                Navigator.of(context).pop(); // Close drawer
                _loadSelectedCompanyDetails(); // Refresh details/notifications from server
              },
              onLogout: _logout,
            ),
      body: SafeArea(
        child: _isLoading
            ? const Center(
                child: CircularProgressIndicator(
                  color: AppPalette.primary,
                ),
              )
            : _errorMessage != null
                ? Center(
                    child: Card(
                      margin: const EdgeInsets.all(24),
                      color: AppPalette.surface,
                      child: Padding(
                        padding: const EdgeInsets.all(24),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(Icons.error_outline, color: AppPalette.danger, size: 48),
                            const SizedBox(height: 16),
                            Text(
                              _errorMessage!,
                              textAlign: TextAlign.center,
                              style: const TextStyle(color: AppPalette.text, fontSize: 16),
                            ),
                            const SizedBox(height: 24),
                            ElevatedButton(
                              onPressed: _loadInitialData,
                              child: const Text('Reintentar'),
                            ),
                          ],
                        ),
                      ),
                    ),
                  )
                : _companies.isEmpty || _selectedProfile == null
                    ? Center(
                        child: Card(
                          margin: const EdgeInsets.all(24),
                          color: AppPalette.surface,
                          child: Padding(
                            padding: const EdgeInsets.all(24),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                const Icon(Icons.person_off_outlined, color: AppPalette.textSoft, size: 56),
                                const SizedBox(height: 16),
                                const Text(
                                  'Sin Perfil de Cliente',
                                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
                                ),
                                const SizedBox(height: 8),
                                const Text(
                                  'No estás registrado como cliente activo en ninguna empresa del sistema POS.',
                                  textAlign: TextAlign.center,
                                  style: TextStyle(color: AppPalette.textSoft),
                                ),
                                const SizedBox(height: 24),
                                ElevatedButton(
                                  onPressed: _logout,
                                  child: const Text('Cerrar Sesión'),
                                ),
                              ],
                            ),
                          ),
                        ),
                      )
                    : RefreshIndicator(
                        onRefresh: _loadSelectedCompanyDetails,
                        color: AppPalette.primary,
                        backgroundColor: AppPalette.surface,
                        child: SingleChildScrollView(
                          physics: const AlwaysScrollableScrollPhysics(),
                          padding: const EdgeInsets.all(16),
                          child: _buildActiveSection(persona),
                        ),
                      ),
      ),
    );
  }

  Widget _buildActiveSection(PersonaEmployee? persona) {
    switch (_currentSectionIndex) {
      case 0:
        return _buildPersonalCard(persona);
      case 1:
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Company Selector
            _buildCompanySelector(),
            const SizedBox(height: 16),

            // Credit Summary Card
            _buildCreditSummaryCard(),
            const SizedBox(height: 24),

            // Action Button
            ElevatedButton.icon(
              onPressed: () {
                Navigator.of(context).pushNamed(
                  AppRouter.credits,
                  arguments: {
                    'companyId': _selectedCompany!.idEmpresa,
                    'companyName': _selectedCompany!.nombre,
                    'clientId': _selectedProfile!.idCliente,
                  },
                );
              },
              icon: const Icon(Icons.account_balance_wallet_outlined, color: AppPalette.text),
              label: const Text('Ver historial de mis Créditos'),
              style: ElevatedButton.styleFrom(
                backgroundColor: AppPalette.primary,
                foregroundColor: AppPalette.text,
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                textStyle: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
          ],
        );
      default:
        return _buildPersonalCard(persona);
    }
  }

  Widget _buildPersonalCard(PersonaEmployee? persona) {
    return Card(
      color: AppPalette.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppPalette.border),
      ),
      elevation: 4,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(
                  backgroundColor: AppPalette.bgSoft,
                  radius: 28,
                  child: const Icon(Icons.person, color: AppPalette.text, size: 32),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        persona?.nombreCompleto ?? 'Cliente POS',
                        style: const TextStyle(
                          color: AppPalette.text,
                          fontWeight: FontWeight.w800,
                          fontSize: 20,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        _userEmail ?? '',
                        style: const TextStyle(
                          color: AppPalette.textSoft,
                          fontSize: 14,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const Divider(height: 32, color: AppPalette.border),
            _buildDetailRow(Icons.badge_outlined, 'Documento', persona?.documento ?? '-'),
            const SizedBox(height: 12),
            _buildDetailRow(Icons.phone_outlined, 'Teléfono', persona?.telefono ?? '-'),
            const SizedBox(height: 12),
            _buildDetailRow(
              Icons.cake_outlined, 
              'Fecha de Nacimiento', 
              persona != null 
                  ? '${persona.fechaNacimiento.day.toString().padLeft(2, '0')}/${persona.fechaNacimiento.month.toString().padLeft(2, '0')}/${persona.fechaNacimiento.year}'
                  : '-'
            ),
            const SizedBox(height: 12),
            _buildDetailRow(
              Icons.wc_outlined, 
              'Género', 
              persona?.genero == 'M' 
                  ? 'Masculino' 
                  : persona?.genero == 'F' 
                      ? 'Femenino' 
                      : 'Otro'
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDetailRow(IconData icon, String label, String value) {
    return Row(
      children: [
        Icon(icon, size: 20, color: AppPalette.textSoft),
        const SizedBox(width: 12),
        Text(
          '$label: ',
          style: const TextStyle(color: AppPalette.textSoft, fontWeight: FontWeight.w600),
        ),
        Expanded(
          child: Text(
            value,
            style: const TextStyle(color: AppPalette.text, fontWeight: FontWeight.bold),
            textAlign: TextAlign.end,
          ),
        ),
      ],
    );
  }

  Widget _buildCompanySelector() {
    return Card(
      color: AppPalette.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppPalette.border),
      ),
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: Row(
          children: [
            const Icon(Icons.business, color: AppPalette.textSoft),
            const SizedBox(width: 12),
            const Text(
              'Empresa:',
              style: TextStyle(fontWeight: FontWeight.w600, color: AppPalette.textSoft),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: DropdownButtonHideUnderline(
                child: DropdownButton<Company>(
                  value: _selectedCompany,
                  items: _companies.map((company) {
                    return DropdownMenuItem<Company>(
                      value: company,
                      child: Text(
                        company.nombre,
                        style: const TextStyle(
                          color: AppPalette.text,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    );
                  }).toList(),
                  onChanged: _onCompanyChanged,
                  icon: const Icon(Icons.keyboard_arrow_down, color: AppPalette.text),
                  dropdownColor: AppPalette.surface,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCreditSummaryCard() {
    final double limit = _limitCredit;
    final double used = _utilizedCredit;
    final double available = _availableCredit;

    final progress = limit > 0 ? (used / limit).clamp(0.0, 1.0) : 0.0;
    final isLimitExceeded = used > limit;

    return Card(
      color: AppPalette.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppPalette.border),
      ),
      elevation: 4,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(
              children: [
                Icon(Icons.credit_card, color: AppPalette.primary, size: 24),
                SizedBox(width: 8),
                Text(
                  'Línea de Crédito',
                  style: TextStyle(
                    color: AppPalette.text,
                    fontWeight: FontWeight.w800,
                    fontSize: 18,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),

            // Progress bar
            Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: LinearProgressIndicator(
                    value: progress,
                    minHeight: 10,
                    backgroundColor: AppPalette.border,
                    valueColor: AlwaysStoppedAnimation<Color>(
                      isLimitExceeded 
                          ? AppPalette.danger 
                          : progress > 0.8 
                              ? AppPalette.warning 
                              : AppPalette.success
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Uso de línea de crédito: ${(progress * 100).toStringAsFixed(0)}%',
                      style: TextStyle(
                        fontSize: 12, 
                        color: isLimitExceeded ? AppPalette.danger : AppPalette.textSoft,
                        fontWeight: isLimitExceeded ? FontWeight.bold : FontWeight.normal,
                      ),
                    ),
                    Text(
                      'Cód: ${_selectedProfile?.codigoCliente ?? ''}',
                      style: const TextStyle(fontSize: 12, color: AppPalette.textSoft),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Financial breakdown
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildFinancialItem(
                  'Límite', 
                  '\$${limit.toStringAsFixed(2)}', 
                  AppPalette.textSoft
                ),
                _buildFinancialItem(
                  'Utilizado', 
                  '\$${used.toStringAsFixed(2)}', 
                  isLimitExceeded ? AppPalette.danger : AppPalette.warning
                ),
                _buildFinancialItem(
                  'Disponible', 
                  '\$${available.toStringAsFixed(2)}', 
                  AppPalette.success
                ),
              ],
            ),

            const Divider(height: 32, color: AppPalette.border),

            // Category detail info
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Categoría de Cliente', style: TextStyle(color: AppPalette.textSoft, fontSize: 12)),
                    const SizedBox(height: 4),
                    Text(
                      _selectedCategory?.nombre ?? 'General',
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                    ),
                  ],
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    const Text('Plazo de Pago', style: TextStyle(color: AppPalette.textSoft, fontSize: 12)),
                    const SizedBox(height: 4),
                    Text(
                      _selectedCategory != null 
                          ? '${_selectedCategory!.plazoCredito} días' 
                          : 'N/A',
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Descuento Base', style: TextStyle(color: AppPalette.textSoft, fontSize: 12)),
                    const SizedBox(height: 4),
                    Text(
                      _selectedCategory != null 
                          ? '${_selectedCategory!.descuentoBase.toStringAsFixed(1)}%' 
                          : '0.0%',
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                    ),
                  ],
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    const Text('Estado de Perfil', style: TextStyle(color: AppPalette.textSoft, fontSize: 12)),
                    const SizedBox(height: 4),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                      decoration: BoxDecoration(
                        color: (_selectedProfile?.activo ?? false) 
                            ? AppPalette.success.withValues(alpha: 0.1) 
                            : AppPalette.danger.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(4),
                        border: Border.all(
                          color: (_selectedProfile?.activo ?? false) 
                              ? AppPalette.success.withValues(alpha: 0.5) 
                              : AppPalette.danger.withValues(alpha: 0.5)
                        ),
                      ),
                      child: Text(
                        (_selectedProfile?.activo ?? false) ? 'ACTIVO' : 'INACTIVO',
                        style: TextStyle(
                          color: (_selectedProfile?.activo ?? false) ? AppPalette.success : AppPalette.danger,
                          fontWeight: FontWeight.bold,
                          fontSize: 11,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFinancialItem(String title, String amount, Color amountColor) {
    return Column(
      children: [
        Text(
          title,
          style: const TextStyle(
            color: AppPalette.textSoft,
            fontSize: 12,
            fontWeight: FontWeight.w600,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          amount,
          style: TextStyle(
            color: amountColor,
            fontSize: 16,
            fontWeight: FontWeight.w800,
          ),
        ),
      ],
    );
  }

  void _showNotificationsBottomSheet() {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppPalette.surface,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (BuildContext context) {
        return DraggableScrollableSheet(
          initialChildSize: 0.6,
          minChildSize: 0.3,
          maxChildSize: 0.9,
          expand: false,
          builder: (context, scrollController) {
            return StatefulBuilder(
              builder: (BuildContext context, StateSetter setModalState) {
                void handleNotificationTap(NotificationModel notification) async {
                  Navigator.of(context).pop(); // Close sheet
                  _markAsRead(notification); // Mark as read and reload details
                  _goToCreditsForCompany(notification.idEmpresa); // Navigate to credits
                }

                if (_notifications.isEmpty) {
                  return Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 48),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Icon(
                          Icons.notifications_off_outlined,
                          size: 64,
                          color: AppPalette.textSoft.withValues(alpha: 0.8),
                        ),
                        const SizedBox(height: 16),
                        const Text(
                          'No tienes notificaciones',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 18,
                            color: AppPalette.text,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Te mantendremos al tanto de cualquier novedad de ${_selectedCompany?.nombre ?? 'tu empresa'}.',
                          textAlign: TextAlign.center,
                          style: const TextStyle(color: AppPalette.textSoft, fontSize: 14),
                        ),
                      ],
                    ),
                  );
                }

                return Column(
                  children: [
                    Center(
                      child: Container(
                        margin: const EdgeInsets.only(top: 12, bottom: 8),
                        width: 40,
                        height: 5,
                        decoration: BoxDecoration(
                          color: AppPalette.border,
                          borderRadius: BorderRadius.circular(2.5),
                        ),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Row(
                            children: [
                              const Icon(Icons.notifications_active_outlined, color: AppPalette.primary),
                              const SizedBox(width: 8),
                              Text(
                                'Historial (${_notifications.length})',
                                style: const TextStyle(
                                  fontWeight: FontWeight.bold,
                                  fontSize: 18,
                                  color: AppPalette.text,
                                ),
                              ),
                            ],
                          ),
                          if (_unreadCount > 0)
                            Text(
                              '$_unreadCount pendientes',
                              style: const TextStyle(
                                color: AppPalette.danger,
                                fontWeight: FontWeight.bold,
                                fontSize: 13,
                              ),
                            ),
                        ],
                      ),
                    ),
                    const Divider(height: 1, color: AppPalette.border),
                    Expanded(
                      child: ListView.separated(
                        controller: scrollController,
                        padding: const EdgeInsets.all(16),
                        itemCount: _notifications.length,
                        separatorBuilder: (context, index) => const Divider(height: 16, color: AppPalette.surface2),
                        itemBuilder: (context, index) {
                          final notification = _notifications[index];
                          final dateFormatted = _formatDateTime(notification.fecha);

                          Color priorityColor = AppPalette.textSoft;
                          if (notification.prioridad > 1) {
                            priorityColor = AppPalette.danger;
                          } else if (notification.prioridad == 1) {
                            priorityColor = AppPalette.warning;
                          }

                          return InkWell(
                            onTap: () {
                              handleNotificationTap(notification);
                            },
                            borderRadius: BorderRadius.circular(8),
                            child: Padding(
                              padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 8),
                              child: Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Padding(
                                    padding: const EdgeInsets.only(top: 2),
                                    child: Icon(
                                      notification.leido 
                                          ? Icons.notifications_none 
                                          : Icons.notifications_active,
                                      color: notification.leido 
                                          ? AppPalette.textSoft.withValues(alpha: 0.5) 
                                          : AppPalette.primary,
                                      size: 22,
                                    ),
                                  ),
                                  const SizedBox(width: 12),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Row(
                                          crossAxisAlignment: CrossAxisAlignment.start,
                                          children: [
                                            Expanded(
                                              child: Text(
                                                notification.titulo,
                                                style: TextStyle(
                                                  fontWeight: notification.leido 
                                                      ? FontWeight.w600 
                                                      : FontWeight.bold,
                                                  fontSize: 14,
                                                  color: AppPalette.text,
                                                ),
                                              ),
                                            ),
                                            if (!notification.leido) ...[
                                              const SizedBox(width: 8),
                                              Container(
                                                width: 8,
                                                height: 8,
                                                decoration: const BoxDecoration(
                                                  color: AppPalette.info,
                                                  shape: BoxShape.circle,
                                                ),
                                              ),
                                            ],
                                          ],
                                        ),
                                        const SizedBox(height: 4),
                                        Text(
                                          notification.mensaje,
                                          style: TextStyle(
                                            color: notification.leido 
                                                ? AppPalette.textSoft 
                                                : AppPalette.textSoft.withValues(alpha: 0.9),
                                            fontSize: 13,
                                          ),
                                        ),
                                        const SizedBox(height: 8),
                                        Row(
                                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                          children: [
                                            Text(
                                              dateFormatted,
                                              style: const TextStyle(
                                                color: AppPalette.textSoft,
                                                fontSize: 11,
                                              ),
                                            ),
                                            Row(
                                              children: [
                                                Container(
                                                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                                                  decoration: BoxDecoration(
                                                    color: AppPalette.primary.withValues(alpha: 0.1),
                                                    borderRadius: BorderRadius.circular(4),
                                                    border: Border.all(
                                                      color: AppPalette.primary.withValues(alpha: 0.3),
                                                    ),
                                                  ),
                                                  child: Text(
                                                    _getCompanyName(notification.idEmpresa),
                                                    style: const TextStyle(
                                                      color: AppPalette.primary,
                                                      fontSize: 9,
                                                      fontWeight: FontWeight.bold,
                                                    ),
                                                  ),
                                                ),
                                                if (notification.prioridad > 0) ...[
                                                  const SizedBox(width: 6),
                                                  Container(
                                                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                                                    decoration: BoxDecoration(
                                                      color: priorityColor.withValues(alpha: 0.1),
                                                      borderRadius: BorderRadius.circular(4),
                                                    ),
                                                    child: Text(
                                                      notification.prioridad > 1 ? 'ALTA' : 'MEDIA',
                                                      style: TextStyle(
                                                        color: priorityColor,
                                                        fontSize: 9,
                                                        fontWeight: FontWeight.bold,
                                                      ),
                                                    ),
                                                  ),
                                                ],
                                              ],
                                            ),
                                          ],
                                        ),
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          );
                        },
                      ),
                    ),
                  ],
                );
              },
            );
          },
        );
      },
    );
  }

  String _getCompanyName(int? companyId) {
    if (companyId == null) return 'Sistema';
    for (final company in _companies) {
      if (company.idEmpresa == companyId) {
        return company.nombre;
      }
    }
    return 'Empresa #$companyId';
  }

  String _formatDateTime(DateTime date) {
    return '${date.day.toString().padLeft(2, '0')}/${date.month.toString().padLeft(2, '0')}/${date.year} ${date.hour.toString().padLeft(2, '0')}:${date.minute.toString().padLeft(2, '0')}';
  }
}
