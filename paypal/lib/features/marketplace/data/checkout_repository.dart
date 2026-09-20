import '../../../core/services/api_client.dart';
import 'checkout_models.dart';

class CheckoutException implements Exception {
  const CheckoutException(this.message);
  final String message;
  @override
  String toString() => message;
}

class CheckoutRepository {
  CheckoutRepository({ApiClient? client}) : _client = client ?? apiClient;
  final ApiClient _client;

  Future<PaypalCheckoutOrder> createOrder({
    required double amountUsd,
    required String referenceId,
  }) async {
    try {
      final data = await _client.post('/paypal/checkout/orders', {
        'amountUsd': amountUsd,
        'referenceId': referenceId,
      });
      if (data is! Map<String, dynamic>) {
        throw const CheckoutException('Phản hồi từ server không hợp lệ.');
      }
      return PaypalCheckoutOrder.fromJson(data);
    } on ApiException catch (e) {
      throw CheckoutException(e.message);
    }
  }

  Future<PaypalCheckoutOrder> captureOrder(String orderId) async {
    try {
      final data = await _client.post('/paypal/checkout/orders/$orderId/capture');
      if (data is! Map<String, dynamic>) {
        throw const CheckoutException('Phản hồi từ server không hợp lệ.');
      }
      return PaypalCheckoutOrder.fromJson(data);
    } on ApiException catch (e) {
      throw CheckoutException(e.message);
    }
  }

  Future<PaypalCheckoutOrder> getOrder(String orderId) async {
    try {
      final data = await _client.get('/paypal/checkout/orders/$orderId');
      if (data is! Map<String, dynamic>) {
        throw const CheckoutException('Phản hồi từ server không hợp lệ.');
      }
      return PaypalCheckoutOrder.fromJson(data);
    } on ApiException catch (e) {
      throw CheckoutException(e.message);
    }
  }
}

final checkoutRepository = CheckoutRepository();
