import 'package:flutter/material.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../shared/widgets/primary_button.dart';
import '../../../../shared/widgets/section_card.dart';

class ReminderActionsCard extends StatelessWidget {
  const ReminderActionsCard({
    super.key,
    required this.isBusy,
    required this.onMarkDone,
    required this.onPostpone,
  });

  final bool isBusy;
  final VoidCallback onMarkDone;
  final VoidCallback onPostpone;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).extension<AppColors>()!;

    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Hôm nay đã giặt cọ chưa?',
              style:
                  TextStyle(fontWeight: FontWeight.w700, color: colors.text)),
          const SizedBox(height: 14),
          PrimaryButton(
            label: 'Đã giặt xong',
            isLoading: isBusy,
            onPressed: onMarkDone,
            icon: Icons.check_circle_outline,
          ),
          const SizedBox(height: 14),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: isBusy ? null : onPostpone,
              icon: Icon(Icons.schedule, color: colors.secondary),
              label: Text('Dời lại vào ngày mai',
                  style: TextStyle(color: colors.secondary)),
              style: OutlinedButton.styleFrom(
                minimumSize: const Size(double.infinity, 52),
                side: BorderSide(color: colors.secondary),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16)),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
