import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_spacing.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/services/auth_service.dart';
import '../../../../shared/widgets/app_background.dart';
import '../../../../shared/widgets/app_top_bar.dart';
import '../../../../shared/widgets/primary_button.dart';
import 'otp_verification_screen.dart';

enum _ForgotPasswordStep { email, newPassword }

class ForgotPasswordScreen extends ConsumerStatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  ConsumerState<ForgotPasswordScreen> createState() =>
      _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends ConsumerState<ForgotPasswordScreen> {
  _ForgotPasswordStep _step = _ForgotPasswordStep.email;
  final _emailController = TextEditingController();
  final _newPasswordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  bool _obscurePassword = true;
  bool _loading = false;
  String? _errorText;
  String? _resetToken;

  @override
  void dispose() {
    _emailController.dispose();
    _newPasswordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  Future<void> _sendOtp() async {
    setState(() {
      _loading = true;
      _errorText = null;
    });
    try {
      final email = _emailController.text.trim();
      await ref.read(authServiceProvider).requestPasswordResetOtp(email: email);
      if (!mounted) return;
      final resetToken = await Navigator.of(context).push<String>(
        MaterialPageRoute(
          builder: (_) => OtpVerificationScreen(email: email),
        ),
      );
      if (!mounted) return;
      if (resetToken != null && resetToken.isNotEmpty) {
        setState(() {
          _resetToken = resetToken;
          _step = _ForgotPasswordStep.newPassword;
        });
      }
    } catch (e) {
      if (!mounted) return;
      setState(() => _errorText = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _resetPassword() async {
    final resetToken = _resetToken;
    if (resetToken == null) return;

    setState(() {
      _loading = true;
      _errorText = null;
    });
    try {
      await ref.read(authServiceProvider).resetPassword(
            resetToken: resetToken,
            newPassword: _newPasswordController.text,
            confirmPassword: _confirmPasswordController.text,
          );
      if (!mounted) return;
      Navigator.of(context).pop();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Đặt lại mật khẩu thành công, vui lòng đăng nhập.'),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _errorText = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Widget _buildEmailStep(AppColors colors) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('Nhập email đã đăng ký để nhận mã OTP',
            style: AppTypography.bodyMuted(colors)),
        const SizedBox(height: AppSpacing.lg),
        Text('Email', style: AppTypography.caption(colors)),
        const SizedBox(height: 8),
        TextField(
          controller: _emailController,
          keyboardType: TextInputType.emailAddress,
          decoration: InputDecoration(
            filled: true,
            fillColor: colors.surface,
            prefixIcon: const Icon(Icons.mail_outline),
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
          label: 'Gửi mã OTP',
          isLoading: _loading,
          onPressed: _sendOtp,
          icon: Icons.send_outlined,
        ),
      ],
    );
  }

  Widget _buildNewPasswordStep(AppColors colors) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('Nhập mật khẩu mới', style: AppTypography.bodyMuted(colors)),
        const SizedBox(height: AppSpacing.lg),
        Text('Mật khẩu mới', style: AppTypography.caption(colors)),
        const SizedBox(height: 8),
        TextField(
          controller: _newPasswordController,
          obscureText: _obscurePassword,
          decoration: InputDecoration(
            filled: true,
            fillColor: colors.surface,
            prefixIcon: const Icon(Icons.lock_outline),
            suffixIcon: IconButton(
              icon: Icon(_obscurePassword
                  ? Icons.visibility_outlined
                  : Icons.visibility_off_outlined),
              onPressed: () =>
                  setState(() => _obscurePassword = !_obscurePassword),
            ),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: BorderSide.none,
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.md),
        Text('Xác nhận mật khẩu mới', style: AppTypography.caption(colors)),
        const SizedBox(height: 8),
        TextField(
          controller: _confirmPasswordController,
          obscureText: _obscurePassword,
          decoration: InputDecoration(
            filled: true,
            fillColor: colors.surface,
            prefixIcon: const Icon(Icons.lock_outline),
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
          label: 'Đặt lại mật khẩu',
          isLoading: _loading,
          onPressed: _resetPassword,
          icon: Icons.lock_reset_outlined,
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).extension<AppColors>()!;

    return Scaffold(
      backgroundColor: colors.background,
      appBar: const AppTopBar(title: 'Quên mật khẩu'),
      body: AppBackground(
        child: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(AppSpacing.md),
            child: switch (_step) {
              _ForgotPasswordStep.email => _buildEmailStep(colors),
              _ForgotPasswordStep.newPassword => _buildNewPasswordStep(colors),
            },
          ),
        ),
      ),
    );
  }
}
