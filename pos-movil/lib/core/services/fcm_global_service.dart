import 'dart:async';

import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

/// Servicio FCM de nivel de aplicación.
///
/// Inicializar una sola vez desde [main.dart] después de [Firebase.initializeApp()].
/// Gestiona:
///   - Canal Android "credit_payments_channel".
///   - Listener [FirebaseMessaging.onMessage] (app en foreground).
///   - Listener [FirebaseMessaging.onMessageOpenedApp] (tap sobre notificación en background).
class FcmGlobalService {
  FcmGlobalService._();
  static final FcmGlobalService instance = FcmGlobalService._();

  final FlutterLocalNotificationsPlugin _localNotifications =
      FlutterLocalNotificationsPlugin();

  StreamSubscription<RemoteMessage>? _onMessageSub;
  StreamSubscription<RemoteMessage>? _onMessageOpenedSub;

  final StreamController<Map<String, dynamic>> _notificationTapController =
      StreamController<Map<String, dynamic>>.broadcast();

  Stream<Map<String, dynamic>> get notificationTaps =>
      _notificationTapController.stream;

  Map<String, dynamic>? _pendingNotificationTap;
  Map<String, dynamic>? get pendingNotificationTap => _pendingNotificationTap;

  void clearPendingNotificationTap() {
    _pendingNotificationTap = null;
  }

  bool _initialized = false;

  /// Inicializa canal Android y registra listeners globales de FCM.
  /// Seguro para llamar múltiples veces — solo ejecuta la lógica la primera vez.
  Future<void> initialize() async {
    if (_initialized) return;

    // 0. Solicitar permisos de notificación al inicializar
    try {
      await FirebaseMessaging.instance.requestPermission(
        alert: true,
        badge: true,
        sound: true,
      );
    } catch (e) {
      debugPrint('[FCM Global] Error al solicitar permisos al inicializar: $e');
    }

    // 1. Inicializar flutter_local_notifications
    const AndroidInitializationSettings initAndroid =
        AndroidInitializationSettings('@mipmap/ic_launcher');
    const InitializationSettings initSettings =
        InitializationSettings(android: initAndroid);

    await _localNotifications.initialize(
      settings: initSettings,
      onDidReceiveNotificationResponse: (NotificationResponse response) {
        debugPrint('[FCM Global] Tap en notificación local: ${response.payload}');
        if (response.payload != null) {
          _notificationTapController.add({'id_empresa': response.payload});
        }
      },
    );

    // 2. Crear canal Android "credit_payments_channel_v2" con Importance.max
    final androidPlugin = _localNotifications
        .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin>();

    await androidPlugin?.createNotificationChannel(
      const AndroidNotificationChannel(
        'credit_payments_channel_v2',
        'Cobros de Crédito',
        description: 'Notificaciones de abonos y pagos de crédito registrados',
        importance: Importance.max,
        playSound: true,
        enableVibration: true,
        enableLights: true,
      ),
    );

    debugPrint('[FCM Global] Canal credit_payments_channel_v2 creado.');

    // 3. Opciones de presentación foreground (iOS)
    await FirebaseMessaging.instance
        .setForegroundNotificationPresentationOptions(
      alert: true,
      badge: true,
      sound: true,
    );

    // 4. Listener foreground — activo sin importar la pantalla
    _onMessageSub?.cancel();
    _onMessageSub =
        FirebaseMessaging.onMessage.listen((RemoteMessage message) {
      debugPrint(
          '[FCM Global Foreground] Recibido: ${message.notification?.title}');
      _showLocalNotification(message);
    });

    // 5. Listener de tap en background
    _onMessageOpenedSub?.cancel();
    _onMessageOpenedSub =
        FirebaseMessaging.onMessageOpenedApp.listen((RemoteMessage message) {
      debugPrint(
          '[FCM Global] Tap en notificación (background): ${message.notification?.title}');
      _notificationTapController.add(message.data);
    });

    // 6. Verificar mensaje inicial (estado terminado)
    try {
      final RemoteMessage? initialMessage =
          await FirebaseMessaging.instance.getInitialMessage();
      if (initialMessage != null) {
        debugPrint(
            '[FCM Global] App abierta desde notificación (terminated): ${initialMessage.notification?.title}');
        _pendingNotificationTap = initialMessage.data;
      }
    } catch (e) {
      debugPrint('[FCM Global] Error al obtener mensaje inicial: $e');
    }

    _initialized = true;
    debugPrint('[FCM Global] Servicio FCM global inicializado correctamente.');
  }

  /// Muestra un banner local para un [RemoteMessage] recibido en foreground.
  void _showLocalNotification(RemoteMessage message) {
    final notification = message.notification;
    debugPrint('[FCM Global] _showLocalNotification: notification=${notification?.title}');
    if (notification == null) {
      debugPrint('[FCM Global] _showLocalNotification: notification es null — se omite banner');
      return;
    }

    const String channelId = 'credit_payments_channel_v2';
    const String channelName = 'Cobros de Crédito';
    const String channelDesc = 'Notificaciones de abonos y pagos de crédito registrados';
    final String? companyIdStr = message.data['id_empresa']?.toString();
    final int notifId = notification.hashCode;

    debugPrint('[FCM Global] Mostrando banner local: title=${notification.title} id=$notifId channelId=$channelId');

    const AndroidNotificationDetails androidDetails = AndroidNotificationDetails(
      channelId,
      channelName,
      channelDescription: channelDesc,
      importance: Importance.max,
      priority: Priority.high,
      playSound: true,
      enableVibration: true,
      icon: '@mipmap/ic_launcher',
      styleInformation: BigTextStyleInformation(''),
    );
    const NotificationDetails notifDetails = NotificationDetails(android: androidDetails);

    _localNotifications
        .show(
          id: notifId,
          title: notification.title,
          body: notification.body,
          notificationDetails: notifDetails,
          payload: companyIdStr,
        )
        .then((_) {
          debugPrint('[FCM Global] Banner local mostrado exitosamente en canal: $channelId');
        })
        .catchError((Object e) {
          debugPrint('[FCM Global] ERROR mostrando banner local: $e');
        });
  }

  /// Libera los listeners.
  void dispose() {
    _onMessageSub?.cancel();
    _onMessageOpenedSub?.cancel();
    _initialized = false;
  }
}
