import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_spacing.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/models/marketplace_job.dart';
import '../../../../shared/widgets/app_background.dart';
import '../../../../shared/widgets/app_top_bar.dart';
import '../../../../shared/widgets/primary_button.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../data/checkout_models.dart';
import '../../data/checkout_repository.dart';

class HireFreelancerScreen extends StatefulWidget {
  const HireFreelancerScreen({super.key, required this.job});

  final MarketplaceJob job;

  @override
  State<HireFreelancerScreen> createState() => _HireFreelancerScreenState();
}

class _HireFreelancerScreenState extends State<HireFreelancerScreen> {
  late final TextEditingController _amountController;
  bool _creating = false;
  bool _capturing = false;
  String? _errorText;
  PaypalCheckoutOrder? _order;

  final _usdFormat = NumberFormat.currency(locale: 'en_US', symbol: r'$');

  @override
  void initState() {
    super.initState();
    _amountController = TextEditingController(text: widget.job.budgetUsd.toStringAsFixed(2));
  }

  @override
  void dispose() {
    _amountController.dispose();
    super.dispose();
  }

  Future<void> _createOrder() async {
    final amount = double.tryParse(_amountController.text.trim().replaceAll(',', '.'));
    if (amount == null || amount <= 0) {
      setState(() => _errorText = 'Nhập số tiền hợp lệ (> 0).');
      return;
    }

    setState(() {
      _creating = true;
      _errorText = null;
    });
    try {
      final order = await checkoutRepository.createOrder(
        amountUsd: amount,
        referenceId: widget.job.id,
      );
      if (!mounted) return;
      setState(() => _order = order);
      final approvalUrl = order.approvalUrl;
      if (approvalUrl != null) {
        await launchUrl(Uri.parse(approvalUrl), mode: LaunchMode.externalApplication);
      }
    } catch (e) {
      if (!mounted) return;
      setState(() => _errorText = e.toString());
    } finally {
      if (mounted) setState(() => _creating = false);
    }
  }

  Future<void> _confirmPayment() async {
    final order = _order;
    if (order == null) return;

    setState(() {
      _capturing = true;
      _errorText = null;
    });
    try {
      final captured = await checkoutRepository.captureOrder(order.id);
      if (!mounted) return;
      setState(() => _order = captured);
    } catch (e) {
      if (!mounted) return;
      setState(() => _errorText = e.toString());
    } finally {
      if (mounted) setState(() => _capturing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).extension<AppColors>()!;
    final order = _order;

    return Scaffold(
      backgroundColor: colors.background,
      appBar: const AppTopBar(title: 'Thuê freelancer'),
      body: AppBackground(
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(AppSpacing.md),
            children: [
              SectionCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(widget.job.title,
                        style: AppTypography.heading2(colors)),
                    const SizedBox(height: 4),
                    Text(widget.job.description, style: AppTypography.bodyMuted(colors)),
                  ],
                ),
              ),
              const SizedBox(height: AppSpacing.md),
              SectionCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Thanh toán qua PayPal', style: AppTypography.heading2(colors)),
                    const SizedBox(height: 4),
                    Text('Tạo order PayPal thật, giữ tiền cho tới khi bạn xác nhận capture.',
                        style: AppTypography.bodyMuted(colors)),
                    const SizedBox(height: AppSpacing.md),
                    Text('Số tiền (USD)', style: AppTypography.caption(colors)),
                    const SizedBox(height: 6),
                    TextField(
                      controller: _amountController,
                      enabled: order == null,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: InputDecoration(
                        filled: true,
                        fillColor: colors.background,
                        prefixIcon: const Icon(Icons.attach_money_rounded),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(14),
                          borderSide: BorderSide.none,
                        ),
                      ),
                    ),
                    if (_errorText != null) ...[
                      const SizedBox(height: AppSpacing.sm),
                      Text(_errorText!, style: TextStyle(color: colors.danger)),
                    ],
                    const SizedBox(height: AppSpacing.md),
                    if (order == null)
                      PrimaryButton(
                        label: 'Thuê & thanh toán qua PayPal',
                        isLoading: _creating,
                        onPressed: _createOrder,
                        icon: Icons.paid_outlined,
                      )
                    else ...[
                      Row(
                        children: [
                          Icon(
                            order.status == 'CAPTURED'
                                ? Icons.check_circle_outline
                                : Icons.hourglass_top_rounded,
                            color: order.status == 'CAPTURED' ? colors.success : colors.textMuted,
                          ),
                          const SizedBox(width: 8),
                          Text('Trạng thái: ${order.status}',
                              style: AppTypography.body(colors).copyWith(fontWeight: FontWeight.w700)),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text('Order PayPal: ${order.paypalOrderId}',
                          style: AppTypography.caption(colors)),
                      const SizedBox(height: AppSpacing.md),
                      if (order.status != 'CAPTURED')
                        PrimaryButton(
                          label: 'Tôi đã thanh toán, xác nhận',
                          isLoading: _capturing,
                          onPressed: _confirmPayment,
                          icon: Icons.check_circle_outline,
                        ),
                    ],
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
