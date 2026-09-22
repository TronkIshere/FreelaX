import 'dart:convert';

import 'package:http/http.dart' as http;

import '../domain/auth_repository.dart';
import '../models/auth_user.dart';
import '../services/app_config_service.dart';
import '../services/http_json.dart';

class RemoteAuthRepository implements AuthRepository {
  RemoteAuthRepository({http.Client? client})
      : _client = client ?? http.Client();

  final http.Client _client;
  static const _basePath = '/api/v1/auth';

  Uri _uri(String path) {
    try {
      return buildUri(AppConfigService.instance.baseUrl, _basePath, path);
    } on HttpJsonException catch (e) {
      throw AuthException(e.message, statusCode: e.statusCode);
    }
  }

  Map<String, String> get _headers => const {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      };

  Future<dynamic> _post(
    String path,
    Map<String, dynamic> body, {
    Map<String, String>? extraHeaders,
    String failMessage = 'Yêu cầu thất bại.',
  }) async {
    final response = await _request(() => _client.post(
          _uri(path),
          headers: {..._headers, ...?extraHeaders},
          body: jsonEncode(body),
        ));
    return _parse(response, failMessage);
  }

  Future<dynamic> _get(
    String path, {
    required Map<String, String> extraHeaders,
    String failMessage = 'Yêu cầu thất bại.',
  }) async {
    final response = await _request(() => _client.get(
          _uri(path),
          headers: {..._headers, ...extraHeaders},
        ));
    return _parse(response, failMessage);
  }

  Future<http.Response> _request(
      Future<http.Response> Function() request) async {
    try {
      return await attemptRequest(request);
    } on HttpJsonException catch (e) {
      throw AuthException(e.message, statusCode: e.statusCode);
    }
  }

  dynamic _parse(http.Response response, String failMessage) {
    try {
      return parseJsonBody(response, failMessage: failMessage);
    } on HttpJsonException catch (e) {
      throw AuthException(e.message, statusCode: e.statusCode);
    }
  }

  @override
  Future<AuthUser> login(
      {required String email, required String password}) async {
    final json = await _post('/sign-in', {
      'email': email,
      'password': password,
    });
    return AuthUser.fromApiJson(json as Map<String, dynamic>? ?? const {});
  }

  @override
  Future<void> register({
    required String email,
    required String password,
    required String displayName,
  }) async {
    await _post('/register', {
      'email': email,
      'password': password,
      'displayName': displayName,
    });
  }

  @override
  Future<void> requestPasswordResetOtp({required String email}) async {
    await _post('/forgot-password/send-otp', {'email': email});
  }

  @override
  Future<String> verifyPasswordResetOtp(
      {required String email, required String otp}) async {
    final resetToken = await _post('/forgot-password/verify-otp', {
      'email': email,
      'otp': otp,
    });
    return resetToken as String? ?? '';
  }

  @override
  Future<void> resetPassword({
    required String resetToken,
    required String newPassword,
    required String confirmPassword,
  }) async {
    await _post('/forgot-password/reset', {
      'resetToken': resetToken,
      'newPassword': newPassword,
      'confirmPassword': confirmPassword,
    });
  }

  @override
  Future<void> logout({required String accessToken}) async {
    try {
      await _post('/sign-out', {'accessToken': accessToken});
    } catch (_) {}
  }

  @override
  Future<String> refreshAccessToken({required String refreshToken}) async {
    final data = await _post(
      '/refresh-token',
      {'refreshToken': refreshToken},
      failMessage: 'Phiên đăng nhập đã hết hạn.',
    );
    final newAccessToken =
        data is Map<String, dynamic> ? data['accessToken'] as String? : null;
    if (newAccessToken == null) {
      throw const AuthException('Không làm mới được phiên đăng nhập.');
    }
    return newAccessToken;
  }

  @override
  Future<AuthUser> getCurrentUser({required String accessToken}) async {
    final data = await _get(
      '/me',
      extraHeaders: {'Authorization': 'Bearer $accessToken'},
      failMessage: 'Không lấy được thông tin tài khoản.',
    );
    return AuthUser.fromApiJson(data as Map<String, dynamic>? ?? const {});
  }
}
