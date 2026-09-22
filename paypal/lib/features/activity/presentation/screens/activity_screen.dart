import 'package:flutter/material.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_spacing.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../shared/widgets/app_background.dart';
import '../../../../shared/widgets/app_top_bar.dart';

class ActivityScreen extends StatelessWidget {
  const ActivityScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).extension<AppColors>()!;
    return Scaffold(
      backgroundColor: colors.background,
      appBar: const AppTopBar(title: 'Hoạt động'),
      body: AppBackground(
        child: SafeArea(
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(AppSpacing.xl),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.receipt_long_outlined,
                      size: 56, color: colors.textMuted),
                  const SizedBox(height: AppSpacing.md),
                  Text(
                    'Lịch sử giao dịch đang được phát triển',
                    textAlign: TextAlign.center,
                    style: AppTypography.heading2(colors),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Tab này sẽ hiển thị lịch sử gửi/nhận tiền khi kết nối với backend thật.',
                    textAlign: TextAlign.center,
                    style: AppTypography.bodyMuted(colors),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
