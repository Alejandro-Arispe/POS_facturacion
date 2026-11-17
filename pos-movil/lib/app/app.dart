import 'package:flutter/material.dart';

import '../core/constants/app_palette.dart';
import 'router.dart';

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'POS Mobile',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        scaffoldBackgroundColor: AppPalette.bg,
        colorScheme: ColorScheme.fromSeed(
          seedColor: AppPalette.primary,
          primary: AppPalette.primary,
          surface: AppPalette.surface,
          error: AppPalette.danger,
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: AppPalette.surface,
          foregroundColor: AppPalette.text,
          elevation: 0,
          centerTitle: true,
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: AppPalette.surface2,
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 16,
            vertical: 14,
          ),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: const BorderSide(color: AppPalette.border),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: const BorderSide(color: AppPalette.border),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: const BorderSide(color: AppPalette.primary, width: 2),
          ),
          errorBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: const BorderSide(color: AppPalette.danger),
          ),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            backgroundColor: AppPalette.primary,
            foregroundColor: AppPalette.text,
            disabledBackgroundColor: AppPalette.primary.withValues(alpha: 0.5),
            disabledForegroundColor: AppPalette.textSoft,
            minimumSize: const Size.fromHeight(48),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(8),
            ),
            textStyle: const TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
        textTheme: Theme.of(context).textTheme.apply(
          bodyColor: AppPalette.text,
          displayColor: AppPalette.text,
        ),
      ),
      initialRoute: AppRouter.login,
      onGenerateRoute: AppRouter.generateRoute,
    );
  }
}
