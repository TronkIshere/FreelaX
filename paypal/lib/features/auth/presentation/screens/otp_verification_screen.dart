import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_spacing.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/services/auth_service.dart';
import '../../../../shared/widgets/app_background.dart';
import '../../../../shared/widgets/app_top_bar.dart';
import '../../../../shared/widgets/primary_button.dart';

class OtpVerificationScreen extends ConsumerStatefulWidget {
  const OtpVerificationScreen({super.key, required this.email});

  final String email;

  @override
  ConsumerState<OtpVerificationScreen> createState() =>
      _OtpVerificationScreenState();
}

class _OtpVerificationScreenState extends ConsumerState<OtpVerificationScreen> {
  final _otpController = TextEditingController();
  bool _loading = false;
  bool _resending = false;
  String? _errorText;

  @override
  void dispose() {
    _otpController.dispose();
    super.dispose();
  }

  Future<void> _verify() async {
    setState(() {
      _loading = true;
      _errorText = null;
    });
    try {
      final resetToken =
          await ref.read(authServiceProvider).verifyPasswordResetOtp(
                email: widget.email,
                otp: _otpController.text.trim(),
              );
      if (!mounted) return;
      Navigator.of(context).pop(resetToken);
    } catch (e) {
      if (!mounted) return;
      setState(() => _errorText = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _resend() async {
    setState(() {
      _resending = true;
      _errorText = null;
    });
    try {
      await ref
          .read(authServiceProvider)
          .requestPasswordResetOtp(email: widget.email);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đã gửi lại mã OTP')),
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _errorText = e.toString());
    } finally {
      if (mounted) setState(() => _resending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).extension<AppColors>()!;

    return Scaffold(
      backgroundColor: colors.background,
      appBar: const AppTopBar(title: 'Xác thực OTP'),
      body: AppBackground(
        child: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(AppSpacing.md),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('Nhập mã OTP đã gửi tới ${widget.email}',
                    style: AppTypography.bodyMuted(colors)),
                const SizedBox(height: AppSpacing.lg),
                Text('Mã OTP', style: AppTypography.caption(colors)),
                const SizedBox(height: 8),
                TextField(
                  controller: _otpController,
                  keyboardType: TextInputType.number,
                  decoration: InputDecoration(
                    filled: true,
                    fillColor: colors.surface,
                    prefixIcon: const Icon(Icons.password_outlined),
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
                const SizedBox(height: AppSpacing.lg),
                PrimaryButton(
                  label: 'Xác thực',
                  isLoading: _loading,
                  onPressed: _verify,
                  icon: Icons.check_circle_outline,
                ),
                const SizedBox(height: AppSpacing.sm),
                Center(
                  child: TextButton(
                    onPressed: _resending ? null : _resend,
                    child:
                        Text(_resending ? 'Đang gửi lại...' : 'Gửi lại mã OTP'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
