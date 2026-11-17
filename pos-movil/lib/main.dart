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
  await Firebase.initializeApp(
    options: DefaultFirebaseOptions.currentPlatform,
  );

  // Registrar handler de background ANTES de runApp
  FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);

  // Inicializar canales Android y listeners foreground globales
  await FcmGlobalService.instance.initialize();

  runApp(const MyApp());
}
