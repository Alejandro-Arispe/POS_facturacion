import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';

import 'app/app.dart';
import 'core/services/fcm_global_service.dart';
import 'firebase_options.dart';

/// Handler de mensajes FCM cuando la app está en background o cerrada.
/// DEBE estar declarado en el nivel superior del archivo (fuera de cualquier clase).
@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  // Firebase ya fue inicializado por el plugin antes de llamar este handler.
  debugPrint('[FCM Background] Mensaje recibido: ${message.notification?.title}');
  debugPrint('[FCM Background] Data: ${message.data}');
  // El banner del sistema lo muestra Android automáticamente
  // cuando el mensaje contiene el campo "notification".
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Firebase solo sirve para las notificaciones push: si falla o tarda, la app igual debe abrir.
  final firebaseListo = await _inicializarFirebase();

  runApp(const MyApp());

  // Los permisos y canales de notificacion se piden despues de mostrar la primera pantalla,
  // asi el dialogo de Android no bloquea el arranque.
  if (firebaseListo) {
    unawaited(FcmGlobalService.instance.initialize().catchError((Object e) {
      debugPrint('[FCM] No se pudo inicializar: $e');
    }));
  }
}

Future<bool> _inicializarFirebase() async {
  try {
    if (Firebase.apps.isEmpty) {
      // En Android la configuracion sale de android/app/google-services.json (el plugin de Gradle ya
      // registra la app); pasar otras opciones provoca el error "duplicate-app" y la app no arranca.
      final usarJson = !kIsWeb && defaultTargetPlatform == TargetPlatform.android;
      await (usarJson
              ? Firebase.initializeApp()
              : Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform))
          .timeout(const Duration(seconds: 10));
    }
    // Registrar handler de background ANTES de runApp
    FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);
    return true;
  } catch (e) {
    debugPrint('[Firebase] Inicializacion omitida: $e');
    return false;
  }
}
