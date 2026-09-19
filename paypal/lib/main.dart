import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/constants/app_colors.dart';
import 'core/constants/app_strings.dart';
import 'core/constants/app_typography.dart';
import 'core/data/remote_auth_repository.dart';
import 'core/services/auth_service.dart';
import 'core/utils/nav_key.dart';
import 'features/splash/presentation/screens/splash_screen.dart';

void main() {
  // Đổi thành MockAuthRepository() nếu muốn demo UI mà chưa cần bật backend.
  AuthService.instance.setRepository(RemoteAuthRepository());
  runApp(const ProviderScope(child: PaySimApp()));
}

class PaySimApp extends StatelessWidget {
  const PaySimApp({super.key});

  @override
  Widget build(BuildContext context) {
    const colors = AppColors.light;

    return MaterialApp(
      navigatorKey: navigatorKey,
      title: AppStrings.appName,
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        scaffoldBackgroundColor: colors.background,
        colorScheme: ColorScheme.fromSeed(
          seedColor: colors.primary,
          primary: colors.primary,
          secondary: colors.secondary,
          surface: colors.surface,
          error: colors.danger,
        ),
        textTheme: AppTypography.buildTextTheme(colors),
        extensions: const [colors],
      ),
      home: const SplashScreen(),
    );
  }
}
