import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_spacing.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../shared/widgets/app_background.dart';
import '../../../../shared/widgets/app_top_bar.dart';
import '../../../../shared/widgets/primary_button.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../data/simulation_models.dart';
import '../../data/simulation_repository.dart';

class CompareFeeScreen extends StatefulWidget {
  const CompareFeeScreen({super.key});

  @override
  State<CompareFeeScreen> createState() => _CompareFeeScreenState();
}

class _CompareFeeScreenState extends State<CompareFeeScreen> {
  final _amountController = TextEditingController(text: '500');
  final _rateController = TextEditingController(text: '25000');
  bool _loading = false;
  String? _errorText;
  PaymentComparisonResult? _result;

  final _usdFormat = NumberFormat.currency(locale: 'en_US', symbol: r'$');
  final _vndFormat =
      NumberFormat.currency(locale: 'vi_VN', symbol: 'đ', decimalDigits: 0);

  @override
  void dispose() {
    _amountController.dispose();
    _rateController.dispose();
    super.dispose();
  }

  Future<void> _compare() async {
    final gross =
        double.tryParse(_amountController.text.trim().replaceAll(',', '.'));
    final rate =
        double.tryParse(_rateController.text.trim().replaceAll(',', '.'));
    if (gross == null || gross <= 0) {
      setState(() => _errorText = 'Nhập số tiền USD hợp lệ (> 0).');
      return;
    }
    if (rate == null || rate <= 0) {
      setState(() => _errorText = 'Nhập tỷ giá USD/VND hợp lệ (> 0).');
      return;
    }

    setState(() {
      _loading = true;
      _errorText = null;
    });
    try {
      final result = await simulationRepository.compare(
        grossAmountUsd: gross,
        midMarketRate: rate,
      );
      if (!mounted) return;
      setState(() => _result = result);
    } catch (e) {
      if (!mounted) return;
      setState(() => _errorText = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).extension<AppColors>()!;

    return Scaffold(
      backgroundColor: colors.background,
      appBar: const AppTopBar(title: 'So sánh phí nhận tiền'),
      body: AppBackground(
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(AppSpacing.md),
            children: [
              SectionCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('PayPal vs USDC + off-ramp hợp pháp',
                        style: AppTypography.heading2(colors)),
                    const SizedBox(height: 4),
                    Text(
                      'Nhập số tiền khách quốc tế trả bạn để xem chênh lệch phí (số liệu minh họa, cần thay bằng bảng phí thật trước khi trình bày chính thức).',
                      style: AppTypography.bodyMuted(colors),
                    ),
                    const SizedBox(height: AppSpacing.md),
                    Text('Số tiền (USD)', style: AppTypography.caption(colors)),
                    const SizedBox(height: 6),
                    TextField(
                      controller: _amountController,
                      keyboardType:
                          const TextInputType.numberWithOptions(decimal: true),
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
                    const SizedBox(height: AppSpacing.sm),
                    Text('Tỷ giá tham chiếu USD/VND',
                        style: AppTypography.caption(colors)),
                    const SizedBox(height: 6),
                    TextField(
                      controller: _rateController,
                      keyboardType:
                          const TextInputType.numberWithOptions(decimal: true),
                      decoration: InputDecoration(
                        filled: true,
                        fillColor: colors.background,
                        prefixIcon: const Icon(Icons.currency_exchange_rounded),
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
                    PrimaryButton(
                      label: 'So sánh ngay',
                      isLoading: _loading,
                      onPressed: _compare,
                      icon: Icons.bolt_rounded,
                    ),
                  ],
                ),
              ),
              if (_result != null) ...[
                const SizedBox(height: AppSpacing.md),
                _RailResultCard(
                  title: 'PayPal (mock)',
                  color: colors.danger,
                  result: _result!.paypal,
                  usdFormat: _usdFormat,
                  vndFormat: _vndFormat,
                ),
                const SizedBox(height: AppSpacing.md),
                _RailResultCard(
                  title: 'USDC + off-ramp + MISA (mock)',
                  color: colors.success,
                  result: _result!.usdcMisa,
                  usdFormat: _usdFormat,
                  vndFormat: _vndFormat,
                ),
                const SizedBox(height: AppSpacing.md),
                SectionCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Bạn tiết kiệm được',
                          style: AppTypography.caption(colors)),
                      const SizedBox(height: 4),
                      Text(_vndFormat.format(_result!.savingsVnd),
                          style: AppTypography.score(colors)),
                      Text(
                        '(~${_result!.savingsPercent.toStringAsFixed(2)}% phí)',
                        style: AppTypography.bodyMuted(colors),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: AppSpacing.sm),
                Text(
                  '* Số liệu minh họa để demo cơ chế tính — thay bằng bảng phí PayPal chính thức + bảng phí sàn off-ramp thật trước khi thuyết trình chính thức.',
                  style: AppTypography.caption(colors),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _RailResultCard extends StatelessWidget {
  const _RailResultCard({
    required this.title,
    required this.color,
    required this.result,
    required this.usdFormat,
    required this.vndFormat,
  });

  final String title;
  final Color color;
  final RailSimulationResult result;
  final NumberFormat usdFormat;
  final NumberFormat vndFormat;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).extension<AppColors>()!;
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 10,
                height: 10,
                decoration: BoxDecoration(color: color, shape: BoxShape.circle),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  title,
                  style: AppTypography.body(colors)
                      .copyWith(fontWeight: FontWeight.w700),
                ),
              ),
              Text(
                '${result.effectiveFeeRatePercent.toStringAsFixed(2)}%',
                style: AppTypography.body(colors)
                    .copyWith(fontWeight: FontWeight.w800, color: color),
              ),
            ],
          ),
          const Divider(height: 20),
          ...result.feeItems.map((item) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Row(
                  children: [
                    Expanded(
                        child: Text(item.label,
                            style: AppTypography.bodyMuted(colors))),
                    Text(usdFormat.format(item.amountUsd),
                        style: AppTypography.bodyMuted(colors)),
                  ],
                ),
              )),
          const Divider(height: 20),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Nhận được (VND)',
                style: AppTypography.body(colors)
                    .copyWith(fontWeight: FontWeight.w700),
              ),
              Text(
                vndFormat.format(result.netVnd),
                style: AppTypography.body(colors)
                    .copyWith(fontWeight: FontWeight.w800),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
