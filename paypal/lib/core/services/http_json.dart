import 'dart:convert';

import 'package:http/http.dart' as http;

class HttpJsonException implements Exception {
  const HttpJsonException(this.message, {this.statusCode});
  final String message;
  final int? statusCode;
  @override
  String toString() => message;
}

const httpJsonTimeout = Duration(seconds: 15);

Uri buildUri(String baseUrl, String basePath, String path) {
  if (baseUrl.isEmpty) {
    throw const HttpJsonException(
        'Chưa cấu hình địa chỉ server. Vào Cài đặt để nhập URL (IP:port của máy chạy backend).');
  }
  return Uri.parse('$baseUrl$basePath$path');
}

Future<http.Response> attemptRequest(
    Future<http.Response> Function() request) async {
  try {
    return await request().timeout(httpJsonTimeout);
  } on HttpJsonException {
    rethrow;
  } catch (_) {
    throw const HttpJsonException(
        'Không kết nối được tới server. Kiểm tra URL và mạng WiFi (điện thoại và laptop phải cùng mạng).');
  }
}

dynamic parseJsonBody(http.Response response,
    {String failMessage = 'Yêu cầu thất bại.'}) {
  dynamic decoded;
  if (response.body.isNotEmpty) {
    try {
      decoded = jsonDecode(response.body);
    } catch (_) {
      decoded = null;
    }
  }

  if (response.statusCode < 200 || response.statusCode >= 300) {
    final message = decoded is Map<String, dynamic>
        ? decoded['error'] as String? ?? failMessage
        : '$failMessage (mã ${response.statusCode}).';
    throw HttpJsonException(message, statusCode: response.statusCode);
  }

  if (decoded is Map<String, dynamic>) return decoded['data'];
  return null;
}
