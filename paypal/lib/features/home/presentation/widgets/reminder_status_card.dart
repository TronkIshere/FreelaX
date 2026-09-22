import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../shared/widgets/primary_button.dart';
import '../../../../shared/widgets/section_card.dart';

class ReminderStatusCard extends StatelessWidget {
  const ReminderStatusCard({
    super.key,
    required this.nextDueAt,
    required this.onOpenReminder,
  });

  final DateTime? nextDueAt;
  final VoidCallback onOpenReminder;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).extension<AppColors>()!;
    final formatter = DateFormat('HH:mm, dd/MM/yyyy');

    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: colors.primary.withOpacity(0.18),
                ),
                child: Icon(Icons.brush_rounded, color: colors.primaryDark),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Lịch giặt cọ kế tiếp',
                        style: TextStyle(
                            fontWeight: FontWeight.w700, color: colors.text)),
                    const SizedBox(height: 2),
                    Text(
                      nextDueAt != null
                          ? formatter.format(nextDueAt!)
                          : 'Chưa đặt lịch nhắc',
                      style: TextStyle(fontSize: 12, color: colors.textMuted),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          PrimaryButton(
            label: 'Xem lịch giặt cọ',
            onPressed: onOpenReminder,
            icon: Icons.chevron_right_rounded,
          ),
        ],
      ),
    );
  }
}
