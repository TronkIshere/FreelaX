import 'dart:convert';

import 'package:http/http.dart' as http;

import '../domain/auth_repository.dart';
import 'app_config_service.dart';
import 'auth_service.dart';
import 'http_json.dart';

class ApiException implements Exception {
  const ApiException(this.message);
  final String message;
  @override
  String toString() => message;
}

class ApiClient {
  ApiClient({http.Client? client}) : _client = client ?? http.Client();

  final http.Client _client;
  static const _basePath = '/api/v1';

  Uri _uri(String path) {
    try {
      return buildUri(AppConfigService.instance.baseUrl, _basePath, path);
    } on HttpJsonException catch (e) {
      throw ApiException(e.message);
    }
  }

  Map<String, String> get _headers {
    final token = AuthService.instance.currentUser?.accessToken;
    return {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      if (token != null) 'Authorization': 'Bearer $token',
    };
  }

  Future<dynamic> _send(Future<http.Response> Function() request) async {
    var response = await _attempt(request);

    if (response.statusCode == 401) {
      try {
        await AuthService.instance.refreshAccessToken();
        response = await _attempt(request);
      } on AuthException catch (e) {
        final isSessionInvalid = e.statusCode == 401 || e.statusCode == 403;
        if (isSessionInvalid) {
          await AuthService.instance.logout();
          rethrow;
        }
      } catch (_) {}
    }

    return _parse(response);
  }

  Future<http.Response> _attempt(
      Future<http.Response> Function() request) async {
    try {
      return await attemptRequest(request);
    } on HttpJsonException catch (e) {
      throw ApiException(e.message);
    }
  }

  dynamic _parse(http.Response response) {
    try {
      return parseJsonBody(response);
    } on HttpJsonException catch (e) {
      throw ApiException(e.message);
    }
  }

  Future<dynamic> get(String path) {
    return _send(() => _client.get(_uri(path), headers: _headers));
  }

  Future<dynamic> post(String path, [Map<String, dynamic>? body]) {
    return _send(() => _client.post(
          _uri(path),
          headers: _headers,
          body: jsonEncode(body ?? const {}),
        ));
  }

  Future<dynamic> put(String path, [Map<String, dynamic>? body]) {
    return _send(() => _client.put(
          _uri(path),
          headers: _headers,
          body: jsonEncode(body ?? const {}),
        ));
  }

  Future<dynamic> delete(String path) {
    return _send(() => _client.delete(_uri(path), headers: _headers));
  }
}

final apiClient = ApiClient();
