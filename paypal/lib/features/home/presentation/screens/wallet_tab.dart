import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_spacing.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/services/auth_service.dart';
import '../../../../shared/widgets/app_background.dart';
import '../../../../shared/widgets/coming_soon.dart';
import '../../../../shared/widgets/section_card.dart';

class WalletTab extends ConsumerWidget {
  const WalletTab({super.key, required this.onOpenCompareFee});

  final VoidCallback onOpenCompareFee;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colors = Theme.of(context).extension<AppColors>()!;
    final user = ref.watch(authServiceProvider).currentUser;
    final greetingName = (user?.displayName?.isNotEmpty ?? false)
        ? user!.displayName!
        : (user?.email ?? 'bạn');

    return Scaffold(
      backgroundColor: colors.background,
      body: AppBackground(
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(AppSpacing.md),
            children: [
              Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Xin chào,',
                            style: AppTypography.bodyMuted(colors)),
                        Text(greetingName,
                            style: AppTypography.heading2(colors)),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: Icon(Icons.notifications_none_rounded,
                        color: colors.text),
                    onPressed: () =>
                        showComingSoon(context, featureName: 'Thông báo'),
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.md),
              const _BalanceCard(),
              const SizedBox(height: AppSpacing.md),
              const _QuickActionsGrid(),
              const SizedBox(height: AppSpacing.md),
              _CompareFeeHighlightCard(onTap: onOpenCompareFee),
              const SizedBox(height: AppSpacing.md),
              SectionCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Hoạt động gần đây',
                        style: AppTypography.heading2(colors)),
                    const SizedBox(height: 12),
                    Text(
                      'Chưa có giao dịch nào trong bản demo này.',
                      style: AppTypography.bodyMuted(colors),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _BalanceCard extends StatelessWidget {
  const _BalanceCard();

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).extension<AppColors>()!;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [colors.primaryDark, colors.primary],
        ),
        boxShadow: [
          BoxShadow(
            color: colors.primary.withOpacity(0.35),
            blurRadius: 20,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Số dư khả dụng',
              style: TextStyle(
                  color: Colors.white70, fontWeight: FontWeight.w600)),
          const SizedBox(height: 6),
          const Text(
            r'$0.00',
            style: TextStyle(
                color: Colors.white, fontSize: 32, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 18),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () =>
                      showComingSoon(context, featureName: 'Gửi tiền'),
                  icon: const Icon(Icons.north_east_rounded,
                      color: Colors.white, size: 18),
                  label: const Text('Gửi tiền',
                      style: TextStyle(color: Colors.white)),
                  style: OutlinedButton.styleFrom(
                    side: const BorderSide(color: Colors.white54),
                    minimumSize: const Size(double.infinity, 44),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () =>
                      showComingSoon(context, featureName: 'Nhận tiền'),
                  icon: const Icon(Icons.south_west_rounded,
                      color: Colors.white, size: 18),
                  label: const Text('Nhận tiền',
                      style: TextStyle(color: Colors.white)),
                  style: OutlinedButton.styleFrom(
                    side: const BorderSide(color: Colors.white54),
                    minimumSize: const Size(double.infinity, 44),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _QuickActionsGrid extends StatelessWidget {
  const _QuickActionsGrid();

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).extension<AppColors>()!;
    const items = [
      (Icons.add_card_rounded, 'Nạp tiền'),
      (Icons.account_balance_outlined, 'Rút tiền'),
      (Icons.qr_code_scanner_rounded, 'Quét mã QR'),
      (Icons.credit_card_outlined, 'Thẻ của tôi'),
    ];
    return Row(
      children: items.map((item) {
        final (icon, label) = item;
        return Expanded(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: InkWell(
              onTap: () => showComingSoon(context, featureName: label),
              borderRadius: BorderRadius.circular(16),
              child: Column(
                children: [
                  Container(
                    width: 52,
                    height: 52,
                    decoration: BoxDecoration(
                      color: colors.primary.withOpacity(0.10),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(icon, color: colors.primary),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    label,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 11,
                      color: colors.textMuted,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      }).toList(),
    );
  }
}

class _CompareFeeHighlightCard extends StatelessWidget {
  const _CompareFeeHighlightCard({required this.onTap});
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).extension<AppColors>()!;
    return SectionCard(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Row(
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: colors.secondary.withOpacity(0.14),
                shape: BoxShape.circle,
              ),
              child: Icon(Icons.bolt_rounded, color: colors.secondary),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'So sánh phí nhận tiền quốc tế',
                    style: AppTypography.body(colors)
                        .copyWith(fontWeight: FontWeight.w700),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    'PayPal vs USDC + off-ramp — xem bạn tiết kiệm được bao nhiêu',
                    style: AppTypography.caption(colors),
                  ),
                ],
              ),
            ),
            Icon(Icons.chevron_right_rounded, color: colors.text),
          ],
        ),
      ),
    );
  }
}
