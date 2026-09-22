import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:uuid/uuid.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_spacing.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/services/auth_service.dart';
import '../../../../shared/widgets/app_background.dart';
import '../../../../shared/widgets/app_top_bar.dart';
import '../../../../shared/widgets/primary_button.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../data/paypal_payee_models.dart';
import '../../data/paypal_payee_repository.dart';
import '../../data/paypal_transaction_models.dart';
import '../../data/paypal_transaction_repository.dart';

enum _ScreenState { loading, error, needsRegistration, ready }

class CompareFeeScreen extends StatefulWidget {
  const CompareFeeScreen({super.key});

  @override
  State<CompareFeeScreen> createState() => _CompareFeeScreenState();
}

class _CompareFeeScreenState extends State<CompareFeeScreen> {
  _ScreenState _state = _ScreenState.loading;
  String? _errorText;
  PaypalPayee? _payee;
  PaypalPayoutTransaction? _lastTransaction;

  // Form đăng ký payee
  final _fullNameController = TextEditingController();
  final _paypalEmailController = TextEditingController();
  final _phoneController = TextEditingController();
  bool _registering = false;

  // Form ghi nhận giao dịch
  final _amountController = TextEditingController(text: '500');
  final _rateController = TextEditingController(text: '25000');
  bool _recording = false;

  final _usdFormat = NumberFormat.currency(locale: 'en_US', symbol: r'$');
  final _vndFormat =
      NumberFormat.currency(locale: 'vi_VN', symbol: 'đ', decimalDigits: 0);

  @override
  void initState() {
    super.initState();
    _loadPayee();
  }

  @override
  void dispose() {
    _fullNameController.dispose();
    _paypalEmailController.dispose();
    _phoneController.dispose();
    _amountController.dispose();
    _rateController.dispose();
    super.dispose();
  }

  Future<void> _loadPayee() async {
    setState(() {
      _state = _ScreenState.loading;
      _errorText = null;
    });
    try {
      final payee = await paypalPayeeRepository.getMine();
      if (!mounted) return;
      if (payee == null) {
        final user = AuthService.instance.currentUser;
        _fullNameController.text = user?.displayName ?? '';
        _paypalEmailController.text = user?.email ?? '';
        setState(() => _state = _ScreenState.needsRegistration);
      } else {
        setState(() {
          _payee = payee;
          _state = _ScreenState.ready;
        });
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _errorText = e.toString();
        _state = _ScreenState.error;
      });
    }
  }

  Future<void> _register() async {
    if (_fullNameController.text.trim().isEmpty ||
        _paypalEmailController.text.trim().isEmpty) {
      setState(() => _errorText = 'Nhập đủ họ tên và email PayPal.');
      return;
    }
    setState(() {
      _registering = true;
      _errorText = null;
    });
    try {
      final payee = await paypalPayeeRepository.register(
        fullName: _fullNameController.text.trim(),
        paypalEmail: _paypalEmailController.text.trim(),
        phone: _phoneController.text.trim(),
      );
      if (!mounted) return;
      setState(() {
        _payee = payee;
        _state = _ScreenState.ready;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _errorText = e.toString());
    } finally {
      if (mounted) setState(() => _registering = false);
    }
  }

  Future<void> _recordTransaction() async {
    final payee = _payee;
    if (payee == null) return;

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
      _recording = true;
      _errorText = null;
    });
    try {
      final transaction = await paypalTransactionRepository.record(
        payeeId: payee.id,
        platformPayoutId: const Uuid().v4(),
        grossAmountUsd: gross,
        midMarketRate: rate,
        description: 'PaySim demo — ghi nhận giao dịch nhận tiền qua PayPal',
        paymentDate: DateTime.now().toIso8601String(),
      );
      if (!mounted) return;
      setState(() => _lastTransaction = transaction);
    } catch (e) {
      if (!mounted) return;
      setState(() => _errorText = e.toString());
    } finally {
      if (mounted) setState(() => _recording = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).extension<AppColors>()!;

    return Scaffold(
      backgroundColor: colors.background,
      appBar: const AppTopBar(title: 'PayPal — ghi nhận giao dịch'),
      body: AppBackground(
        child: SafeArea(
          child: switch (_state) {
            _ScreenState.loading =>
              const Center(child: CircularProgressIndicator()),
            _ScreenState.error => _buildErrorState(colors),
            _ScreenState.needsRegistration => _buildRegistrationForm(colors),
            _ScreenState.ready => _buildTransactionFlow(colors),
          },
        ),
      ),
    );
  }

  Widget _buildErrorState(AppColors colors) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.error_outline_rounded, size: 48, color: colors.danger),
            const SizedBox(height: AppSpacing.md),
            Text(_errorText ?? 'Có lỗi xảy ra.',
                textAlign: TextAlign.center,
                style: AppTypography.bodyMuted(colors)),
            const SizedBox(height: AppSpacing.md),
            PrimaryButton(
              label: 'Thử lại',
              onPressed: _loadPayee,
              icon: Icons.refresh_rounded,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRegistrationForm(AppColors colors) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(AppSpacing.md),
      child: SectionCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Đăng ký hồ sơ nhận tiền PayPal',
                style: AppTypography.heading2(colors)),
            const SizedBox(height: 4),
            Text(
              'Cần đăng ký 1 lần trước khi ghi nhận giao dịch (POST /api/v1/paypal/payees).',
              style: AppTypography.bodyMuted(colors),
            ),
            const SizedBox(height: AppSpacing.md),
            Text('Họ tên', style: AppTypography.caption(colors)),
            const SizedBox(height: 6),
            TextField(
              controller: _fullNameController,
              decoration: InputDecoration(
                filled: true,
                fillColor: colors.background,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.sm),
            Text('Email PayPal', style: AppTypography.caption(colors)),
            const SizedBox(height: 6),
            TextField(
              controller: _paypalEmailController,
              keyboardType: TextInputType.emailAddress,
              decoration: InputDecoration(
                filled: true,
                fillColor: colors.background,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.sm),
            Text('Số điện thoại (không bắt buộc)',
                style: AppTypography.caption(colors)),
            const SizedBox(height: 6),
            TextField(
              controller: _phoneController,
              keyboardType: TextInputType.phone,
              decoration: InputDecoration(
                filled: true,
                fillColor: colors.background,
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
              label: 'Đăng ký',
              isLoading: _registering,
              onPressed: _register,
              icon: Icons.person_add_alt_1_rounded,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTransactionFlow(AppColors colors) {
    final payee = _payee!;
    return ListView(
      padding: const EdgeInsets.all(AppSpacing.md),
      children: [
        SectionCard(
          child: Row(
            children: [
              Icon(Icons.verified_user_outlined, color: colors.success),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  'Hồ sơ payee: ${payee.fullName} (${payee.paypalEmail})',
                  style: AppTypography.caption(colors),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.md),
        SectionCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Ghi nhận giao dịch nhận tiền qua PayPal',
                  style: AppTypography.heading2(colors)),
              const SizedBox(height: 4),
              Text(
                'Gọi thẳng POST /api/v1/paypal/payees/{payeeId}/transactions — số liệu phí là mock, xem docs/PAYPAL_MODULE_REFERENCE.md.',
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
                label: 'Ghi nhận giao dịch',
                isLoading: _recording,
                onPressed: _recordTransaction,
                icon: Icons.bolt_rounded,
              ),
            ],
          ),
        ),
        if (_lastTransaction != null) ...[
          const SizedBox(height: AppSpacing.md),
          _TransactionResultCard(
            transaction: _lastTransaction!,
            usdFormat: _usdFormat,
            vndFormat: _vndFormat,
          ),
        ],
        const SizedBox(height: AppSpacing.md),
        SectionCard(
          child: Row(
            children: [
              Icon(Icons.hourglass_top_rounded, color: colors.textMuted),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  'So sánh với USDC + off-ramp + MISA và số tiền tiết kiệm được — chưa làm, chờ nối misa-backend.',
                  style: AppTypography.caption(colors),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _TransactionResultCard extends StatelessWidget {
  const _TransactionResultCard({
    required this.transaction,
    required this.usdFormat,
    required this.vndFormat,
  });

  final PaypalPayoutTransaction transaction;
  final NumberFormat usdFormat;
  final NumberFormat vndFormat;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).extension<AppColors>()!;
    final fee = transaction.feeBreakdown;

    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 10,
                height: 10,
                decoration:
                    BoxDecoration(color: colors.danger, shape: BoxShape.circle),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text('PayPal (mock)',
                    style: AppTypography.body(colors)
                        .copyWith(fontWeight: FontWeight.w700)),
              ),
              Text(
                '${fee.effectiveFeeRatePercent.toStringAsFixed(2)}%',
                style: AppTypography.body(colors).copyWith(
                    fontWeight: FontWeight.w800, color: colors.danger),
              ),
            ],
          ),
          const Divider(height: 20),
          _row(colors, 'Phí giao dịch thương mại',
              usdFormat.format(fee.commercialFeeUsd)),
          _row(colors, 'Chênh lệch tỷ giá (FX spread)',
              usdFormat.format(fee.fxSpreadCostUsd)),
          const Divider(height: 20),
          _row(colors, 'Trạng thái', transaction.status, bold: true),
          _row(colors, 'Nhận được (VND)', vndFormat.format(transaction.netVnd),
              bold: true),
          const SizedBox(height: 8),
          Text('ID giao dịch: ${transaction.id}',
              style: AppTypography.caption(colors)),
        ],
      ),
    );
  }

  Widget _row(AppColors colors, String label, String value,
      {bool bold = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: AppTypography.bodyMuted(colors)),
          Text(
            value,
            style: bold
                ? AppTypography.body(colors)
                    .copyWith(fontWeight: FontWeight.w800)
                : AppTypography.bodyMuted(colors),
          ),
        ],
      ),
    );
  }
}
