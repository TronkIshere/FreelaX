import '../domain/auth_repository.dart';
import '../models/auth_user.dart';

class MockAuthRepository implements AuthRepository {
  static const demoEmail = 'demo@paysim.local';
  static const demoPassword = 'Demo@1234';
  static const _demoOtp = '123456';
  static const _demoResetToken = 'mock-reset-token';

  final Map<String, _PendingRegistration> _pending = {};
  String? _resetEmail;

  @override
  Future<AuthUser> login(
      {required String email, required String password}) async {
    await Future.delayed(const Duration(milliseconds: 400));
    if (email.trim() != demoEmail || password != demoPassword) {
      throw const AuthException('Email hoặc mật khẩu không đúng.');
    }
    return const AuthUser(
      id: 'demo-user-1',
      email: demoEmail,
      displayName: 'Người dùng Demo',
      accessToken: 'mock-access-token',
      refreshToken: 'mock-refresh-token',
    );
  }

  @override
  Future<void> register({
    required String email,
    required String password,
    required String displayName,
  }) async {
    await Future.delayed(const Duration(milliseconds: 400));
    _pending[email] = _PendingRegistration(
      email: email,
      password: password,
      displayName: displayName,
    );
  }

  @override
  Future<void> requestPasswordResetOtp({required String email}) async {
    await Future.delayed(const Duration(milliseconds: 300));
    _resetEmail = email;
  }

  @override
  Future<String> verifyPasswordResetOtp(
      {required String email, required String otp}) async {
    await Future.delayed(const Duration(milliseconds: 300));
    if (_resetEmail != email) {
      throw const AuthException('Không tìm thấy yêu cầu đặt lại mật khẩu.');
    }
    if (otp != _demoOtp) {
      throw const AuthException('Mã OTP không đúng.');
    }
    return _demoResetToken;
  }

  @override
  Future<void> resetPassword({
    required String resetToken,
    required String newPassword,
    required String confirmPassword,
  }) async {
    await Future.delayed(const Duration(milliseconds: 300));
    if (resetToken != _demoResetToken) {
      throw const AuthException('Phiên đặt lại mật khẩu không hợp lệ.');
    }
    if (newPassword != confirmPassword) {
      throw const AuthException('Mật khẩu xác nhận không khớp.');
    }
    _resetEmail = null;
  }

  @override
  Future<void> logout({required String accessToken}) async {}

  @override
  Future<String> refreshAccessToken({required String refreshToken}) async {
    await Future.delayed(const Duration(milliseconds: 200));
    if (refreshToken != 'mock-refresh-token') {
      throw const AuthException('Phiên đăng nhập đã hết hạn.');
    }
    return 'mock-access-token-refreshed';
  }

  @override
  Future<AuthUser> getCurrentUser({required String accessToken}) async {
    await Future.delayed(const Duration(milliseconds: 200));
    return const AuthUser(
      id: 'demo-user-1',
      email: demoEmail,
      displayName: 'Người dùng Demo',
    );
  }
}

class _PendingRegistration {
  const _PendingRegistration({
    required this.email,
    required this.password,
    required this.displayName,
  });

  final String email;
  final String password;
  final String displayName;
}
