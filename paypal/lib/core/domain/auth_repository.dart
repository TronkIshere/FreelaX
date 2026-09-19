import '../models/auth_user.dart';

class AuthException implements Exception {
  const AuthException(this.message, {this.statusCode});
  final String message;
  final int? statusCode;
  @override
  String toString() => message;
}

abstract class AuthRepository {
  Future<AuthUser> login({required String email, required String password});

  Future<void> register({
    required String email,
    required String password,
    required String displayName,
  });

  Future<void> requestPasswordResetOtp({required String email});

  Future<String> verifyPasswordResetOtp(
      {required String email, required String otp});

  Future<void> resetPassword({
    required String resetToken,
    required String newPassword,
    required String confirmPassword,
  });

  Future<void> logout({required String accessToken});

  Future<String> refreshAccessToken({required String refreshToken});

  Future<AuthUser> getCurrentUser({required String accessToken});
}
