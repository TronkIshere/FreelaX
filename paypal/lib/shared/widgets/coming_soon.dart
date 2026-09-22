import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';
import '../../core/constants/app_typography.dart';

Future<void> showComingSoon(BuildContext context,
    {String featureName = 'Tính năng này'}) {
  final colors = Theme.of(context).extension<AppColors>()!;
  return showModalBottomSheet(
    context: context,
    backgroundColor: colors.surface,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
    ),
    builder: (context) => Padding(
      padding: const EdgeInsets.fromLTRB(24, 28, 24, 36),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: colors.primary.withOpacity(0.12),
            ),
            child: Icon(Icons.construction_rounded, color: colors.primary),
          ),
          const SizedBox(height: 16),
          Text(
            '$featureName đang được phát triển',
            textAlign: TextAlign.center,
            style: AppTypography.heading2(colors),
          ),
          const SizedBox(height: 8),
          Text(
            'Chức năng này chưa được nối với API thật. Cảm ơn bạn đã dùng thử bản demo!',
            textAlign: TextAlign.center,
            style: AppTypography.bodyMuted(colors),
          ),
          const SizedBox(height: 20),
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Đã hiểu'),
            ),
          ),
        ],
      ),
    ),
  );
}
