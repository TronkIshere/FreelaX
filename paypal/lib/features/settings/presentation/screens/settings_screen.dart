import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_spacing.dart';
import '../../../../core/constants/app_strings.dart';
import '../../../../core/services/auth_service.dart';
import '../../../../shared/widgets/app_background.dart';
import '../../../../shared/widgets/app_top_bar.dart';
import '../../../auth/presentation/screens/login_screen.dart';
import '../widgets/server_settings_section.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colors = Theme.of(context).extension<AppColors>()!;
    final user = ref.watch(authServiceProvider).currentUser;

    return Scaffold(
      backgroundColor: colors.background,
      appBar: const AppTopBar(title: 'Cài đặt'),
      body: AppBackground(
        child: ListView(
          children: [
            if (user != null)
              ListTile(
                leading: Icon(Icons.person_outline, color: colors.primary),
                title: Text(
                  (user.displayName?.isNotEmpty ?? false)
                      ? user.displayName!
                      : user.email,
                  style: TextStyle(color: colors.text),
                ),
                subtitle:
                    Text(user.email, style: TextStyle(color: colors.textMuted)),
              ),
            const Padding(
              padding: EdgeInsets.fromLTRB(
                  AppSpacing.md, AppSpacing.lg, AppSpacing.md, AppSpacing.sm),
              child: ServerSettingsSection(),
            ),
            ListTile(
              leading: Icon(Icons.info_outline, color: colors.primary),
              title: Text('Về ${AppStrings.appName}',
                  style: TextStyle(color: colors.text)),
              subtitle: Text('Bản demo mô phỏng — phiên bản 0.1.0',
                  style: TextStyle(color: colors.textMuted)),
            ),
            ListTile(
              leading: Icon(Icons.logout, color: colors.danger),
              title: Text('Đăng xuất', style: TextStyle(color: colors.text)),
              onTap: () async {
                await ref.read(authServiceProvider).logout();
                if (context.mounted) {
                  Navigator.of(context).pushAndRemoveUntil(
                    MaterialPageRoute(builder: (_) => const LoginScreen()),
                    (route) => false,
                  );
                }
              },
            ),
          ],
        ),
      ),
    );
  }
}
