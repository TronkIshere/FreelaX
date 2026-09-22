import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../data/mock_auth_repository.dart';
import '../domain/auth_repository.dart';
import '../models/auth_user.dart';

class AuthService extends ChangeNotifier {
  AuthService._();
  static final AuthService instance = AuthService._();

  static const _userKey = 'paysim_auth_user';
  static const _secureStorage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );

  AuthRepository _repository = MockAuthRepository();

  AuthUser? _currentUser;
  bool _loaded = false;

  AuthUser? get currentUser => _currentUser;
  bool get isLoggedIn => _currentUser != null;

  void setRepository(AuthRepository repository) {
    _repository = repository;
  }

  Future<void> _persistCurrentUser() async {
    final user = _currentUser;
    if (user == null) {
      await _secureStorage.delete(key: _userKey);
      return;
    }
    await _secureStorage.write(key: _userKey, value: jsonEncode(user.toJson()));
  }

  Future<void> load() async {
    if (_loaded) return;
    final raw = await _secureStorage.read(key: _userKey);
    if (raw != null) {
      _currentUser = AuthUser.fromJson(jsonDecode(raw) as Map<String, dynamic>);
    }
    _loaded = true;
    notifyListeners();
  }

  Future<AuthUser> login(
      {required String email, required String password}) async {
    final user = await _repository.login(email: email, password: password);
    _currentUser = user;
    await _persistCurrentUser();
    notifyListeners();
    if (user.displayName == null || user.displayName!.isEmpty) {
      await refreshProfile();
    }
    return user;
  }

  Future<void> refreshProfile() async {
    final accessToken = _currentUser?.accessToken;
    final user = _currentUser;
    if (accessToken == null || user == null) return;
    try {
      final profile =
          await _repository.getCurrentUser(accessToken: accessToken);
      _currentUser = AuthUser(
        id: user.id,
        email: user.email,
        displayName: profile.displayName ?? user.displayName,
        accessToken: user.accessToken,
        refreshToken: user.refreshToken,
      );
      await _persistCurrentUser();
      notifyListeners();
    } catch (_) {}
  }

  Future<void> register({
    required String email,
    required String password,
    required String displayName,
  }) async {
    await _repository.register(
      email: email,
      password: password,
      displayName: displayName,
    );
  }

  Future<void> requestPasswordResetOtp({required String email}) {
    return _repository.requestPasswordResetOtp(email: email);
  }

  Future<String> verifyPasswordResetOtp(
      {required String email, required String otp}) {
    return _repository.verifyPasswordResetOtp(email: email, otp: otp);
  }

  Future<void> resetPassword({
    required String resetToken,
    required String newPassword,
    required String confirmPassword,
  }) {
    return _repository.resetPassword(
      resetToken: resetToken,
      newPassword: newPassword,
      confirmPassword: confirmPassword,
    );
  }

  Future<void> logout() async {
    final accessToken = _currentUser?.accessToken;
    if (accessToken != null) {
      await _repository.logout(accessToken: accessToken);
    }
    _currentUser = null;
    await _persistCurrentUser();
    notifyListeners();
  }

  Future<String> refreshAccessToken() async {
    final refreshToken = _currentUser?.refreshToken;
    final user = _currentUser;
    if (refreshToken == null || user == null) {
      throw const AuthException('Chưa đăng nhập.', statusCode: 401);
    }
    final newAccessToken =
        await _repository.refreshAccessToken(refreshToken: refreshToken);
    _currentUser = AuthUser(
      id: user.id,
      email: user.email,
      displayName: user.displayName,
      accessToken: newAccessToken,
      refreshToken: user.refreshToken,
    );
    await _persistCurrentUser();
    notifyListeners();
    return newAccessToken;
  }
}

final authServiceProvider = ChangeNotifierProvider<AuthService>((ref) {
  return AuthService.instance;
});
