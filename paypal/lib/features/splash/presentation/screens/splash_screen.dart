import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_spacing.dart';
import '../../../../core/constants/app_strings.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/services/auth_service.dart';
import '../../../../shared/widgets/app_background.dart';
import '../../../../shared/widgets/app_logo.dart';
import '../../../auth/presentation/screens/login_screen.dart';
import '../../../home/presentation/screens/home_shell_screen.dart';

final splashInitProvider = FutureProvider.autoDispose<bool>((ref) async {
  await ref.read(authServiceProvider).load();
  return AuthService.instance.isLoggedIn;
});

class SplashScreen extends ConsumerWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colors = Theme.of(context).extension<AppColors>()!;
    final initState = ref.watch(splashInitProvider);

    ref.listen<AsyncValue<bool>>(splashInitProvider, (previous, next) {
      next.whenData((isLoggedIn) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (!context.mounted) return;
          Navigator.of(context).pushReplacement(
            MaterialPageRoute(
              builder: (_) =>
                  isLoggedIn ? const HomeShellScreen() : const LoginScreen(),
            ),
          );
        });
      });
    });

    return Scaffold(
      backgroundColor: colors.background,
      body: AppBackground(
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const AppLogo(size: 120),
              const SizedBox(height: AppSpacing.md),
              Text(AppStrings.appName, style: AppTypography.heading1(colors)),
              const SizedBox(height: 4),
              Text(
                AppStrings.appTagline,
                textAlign: TextAlign.center,
                style: AppTypography.bodyMuted(colors),
              ),
              const SizedBox(height: AppSpacing.xl),
              initState.when(
                data: (_) => const SizedBox.shrink(),
                loading: () => CircularProgressIndicator(
                  valueColor: AlwaysStoppedAnimation(colors.primary),
                ),
                error: (error, stackTrace) => Padding(
                  padding:
                      const EdgeInsets.symmetric(horizontal: AppSpacing.xl),
                  child: Text(
                    'Không thể khởi tạo ứng dụng: $error',
                    textAlign: TextAlign.center,
                    style: AppTypography.caption(colors)
                        .copyWith(color: colors.danger),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
