import 'package:flutter/material.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/points_config.dart';
import '../../../../shared/widgets/section_card.dart';

class StockTrackerCard extends StatelessWidget {
  const StockTrackerCard({super.key, required this.stock});

  final int stock;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).extension<AppColors>()!;
    final ratio = stock / PointsConfig.boxCapacity;
    final low = stock <= 3;

    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Viên sủi còn lại',
                  style: TextStyle(fontWeight: FontWeight.w700, color: colors.text)),
              Text('$stock / ${PointsConfig.boxCapacity}',
                  style: TextStyle(
                      fontWeight: FontWeight.w800,
                      fontSize: 18,
                      color: low ? colors.danger : colors.secondary)),
            ],
          ),
          const SizedBox(height: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(999),
            child: LinearProgressIndicator(
              value: ratio.clamp(0.0, 1.0),
              minHeight: 10,
              backgroundColor: colors.secondary.withOpacity(0.10),
              valueColor: AlwaysStoppedAnimation(
                  low ? colors.danger : colors.secondary),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            low
                ? 'Sắp hết viên, hãy đổi hộp mới khi đủ điểm nhé!'
                : 'Mỗi lần check-in sẽ tự trừ 1 viên khỏi hộp.',
            style: TextStyle(fontSize: 12, color: colors.textMuted),
          ),
        ],
      ),
    );
  }
}
