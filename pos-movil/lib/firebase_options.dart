// Configuración de Firebase del proyecto "atemporal-f0125".
// Regenerable con: flutterfire configure --project=atemporal-f0125
// ignore_for_file: type=lint
import 'package:firebase_core/firebase_core.dart' show FirebaseOptions;
import 'package:flutter/foundation.dart'
    show defaultTargetPlatform, kIsWeb, TargetPlatform;

class DefaultFirebaseOptions {
  static FirebaseOptions get currentPlatform {
    if (kIsWeb) {
      return web;
    }
    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
        return android;
      case TargetPlatform.windows:
        return windows;
      default:
        throw UnsupportedError(
          'Firebase no está configurado para $defaultTargetPlatform en el '
          'proyecto atemporal-f0125 (solo Android y Web). Registra la app en '
          'Firebase y ejecuta flutterfire configure.',
        );
    }
  }

  static const FirebaseOptions web = FirebaseOptions(
    apiKey: 'AIzaSyB52zwwfpkRUkhaQoacF62xCeE_R2mB2pY',
    appId: '1:314117413244:web:47cfea19f16e77ef35acf9',
    messagingSenderId: '314117413244',
    projectId: 'atemporal-f0125',
    authDomain: 'atemporal-f0125.firebaseapp.com',
    storageBucket: 'atemporal-f0125.firebasestorage.app',
    measurementId: 'G-XSCZM6W9KY',
  );

  static const FirebaseOptions android = FirebaseOptions(
    apiKey: 'AIzaSyCIiqZirsiRFkCrbXZ73yxrGeLU7NQy97E',
    appId: '1:314117413244:android:66c1bafb0373131b35acf9',
    messagingSenderId: '314117413244',
    projectId: 'atemporal-f0125',
    storageBucket: 'atemporal-f0125.firebasestorage.app',
  );

  // Windows reutiliza la app web (no hay app nativa de Windows en Firebase).
  static const FirebaseOptions windows = FirebaseOptions(
    apiKey: 'AIzaSyB52zwwfpkRUkhaQoacF62xCeE_R2mB2pY',
    appId: '1:314117413244:web:47cfea19f16e77ef35acf9',
    messagingSenderId: '314117413244',
    projectId: 'atemporal-f0125',
    authDomain: 'atemporal-f0125.firebaseapp.com',
    storageBucket: 'atemporal-f0125.firebasestorage.app',
  );
}
