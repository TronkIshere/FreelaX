import '../../../core/services/api_client.dart';
import 'paypal_payee_models.dart';

class PaypalPayeeException implements Exception {
  const PaypalPayeeException(this.message);
  final String message;
  @override
  String toString() => message;
}

class PaypalPayeeRepository {
  PaypalPayeeRepository({ApiClient? client}) : _client = client ?? apiClient;
  final ApiClient _client;

  Future<PaypalPayee?> getMine() async {
    try {
      final data = await _client.get('/paypal/payees/me');
      if (data is! Map<String, dynamic>) return null;
      return PaypalPayee.fromJson(data);
    } on ApiException catch (e) {
      if (e.statusCode == 404) return null;
      throw PaypalPayeeException(e.message);
    }
  }

  Future<PaypalPayee> register({
    required String fullName,
    required String paypalEmail,
    String? phone,
    String? address,
    String? nationality,
  }) async {
    try {
      final data = await _client.post('/paypal/payees', {
        'fullName': fullName,
        'paypalEmail': paypalEmail,
        if (phone != null && phone.isNotEmpty) 'phone': phone,
        if (address != null && address.isNotEmpty) 'address': address,
        if (nationality != null && nationality.isNotEmpty)
          'nationality': nationality,
      });
      if (data is! Map<String, dynamic>) {
        throw const PaypalPayeeException('Phản hồi từ server không hợp lệ.');
      }
      return PaypalPayee.fromJson(data);
    } on ApiException catch (e) {
      throw PaypalPayeeException(e.message);
    }
  }
}

final paypalPayeeRepository = PaypalPayeeRepository();
