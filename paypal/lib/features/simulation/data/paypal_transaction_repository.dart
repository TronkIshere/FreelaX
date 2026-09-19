import '../../../core/services/api_client.dart';
import 'paypal_transaction_models.dart';

class PaypalTransactionException implements Exception {
  const PaypalTransactionException(this.message);
  final String message;
  @override
  String toString() => message;
}

class PaypalTransactionRepository {
  PaypalTransactionRepository({ApiClient? client})
      : _client = client ?? apiClient;
  final ApiClient _client;

  Future<PaypalPayoutTransaction> record({
    required String payeeId,
    required String platformPayoutId,
    required double grossAmountUsd,
    required double midMarketRate,
    String? senderReference,
    String? description,
    String? paymentDate,
  }) async {
    try {
      final data = await _client.post(
        '/paypal/payees/$payeeId/transactions',
        {
          'platformPayoutId': platformPayoutId,
          'grossAmountUsd': grossAmountUsd,
          'midMarketRate': midMarketRate,
          if (senderReference != null && senderReference.isNotEmpty)
            'senderReference': senderReference,
          if (description != null && description.isNotEmpty)
            'description': description,
          if (paymentDate != null) 'paymentDate': paymentDate,
        },
      );
      if (data is! Map<String, dynamic>) {
        throw const PaypalTransactionException(
            'Phản hồi từ server không hợp lệ.');
      }
      return PaypalPayoutTransaction.fromJson(data);
    } on ApiException catch (e) {
      throw PaypalTransactionException(e.message);
    }
  }
}

final paypalTransactionRepository = PaypalTransactionRepository();
