import '../../../core/services/api_client.dart';
import 'simulation_models.dart';

class SimulationException implements Exception {
  const SimulationException(this.message);
  final String message;
  @override
  String toString() => message;
}

class SimulationRepository {
  SimulationRepository({ApiClient? client}) : _client = client ?? apiClient;
  final ApiClient _client;

  Future<PaymentComparisonResult> compare({
    required double grossAmountUsd,
    required double midMarketRate,
  }) async {
    try {
      final data = await _client.post('/simulations/compare', {
        'grossAmountUsd': grossAmountUsd,
        'midMarketRate': midMarketRate,
      });
      if (data is! Map<String, dynamic>) {
        throw const SimulationException('Phản hồi từ server không hợp lệ.');
      }
      return PaymentComparisonResult.fromJson(data);
    } on ApiException catch (e) {
      throw SimulationException(e.message);
    }
  }
}

final simulationRepository = SimulationRepository();
