library;

class FeeLineItem {
  const FeeLineItem({
    required this.label,
    required this.amountUsd,
    this.description,
  });

  final String label;
  final double amountUsd;
  final String? description;

  factory FeeLineItem.fromJson(Map<String, dynamic> json) => FeeLineItem(
        label: json['label'] as String? ?? '',
        amountUsd: (json['amountUsd'] as num?)?.toDouble() ?? 0,
        description: json['description'] as String?,
      );
}

class RailSimulationResult {
  const RailSimulationResult({
    required this.railName,
    required this.grossUsd,
    required this.feeItems,
    required this.netVnd,
    required this.effectiveFeeRatePercent,
  });

  final String railName;
  final double grossUsd;
  final List<FeeLineItem> feeItems;
  final double netVnd;
  final double effectiveFeeRatePercent;

  factory RailSimulationResult.fromJson(
    Map<String, dynamic> json, {
    required String fallbackRailName,
  }) {
    return RailSimulationResult(
      railName: json['railName'] as String? ?? fallbackRailName,
      grossUsd: (json['grossUsd'] as num?)?.toDouble() ?? 0,
      feeItems: (json['feeItems'] as List<dynamic>? ?? const [])
          .map((e) => FeeLineItem.fromJson(e as Map<String, dynamic>))
          .toList(),
      netVnd: (json['netVnd'] as num?)?.toDouble() ?? 0,
      effectiveFeeRatePercent:
          (json['effectiveFeeRatePercent'] as num?)?.toDouble() ?? 0,
    );
  }
}

class PaymentComparisonResult {
  const PaymentComparisonResult({
    required this.paypal,
    required this.usdcMisa,
    required this.savingsVnd,
    required this.savingsPercent,
  });

  final RailSimulationResult paypal;
  final RailSimulationResult usdcMisa;
  final double savingsVnd;
  final double savingsPercent;

  factory PaymentComparisonResult.fromJson(Map<String, dynamic> json) {
    return PaymentComparisonResult(
      paypal: RailSimulationResult.fromJson(
        json['paypal'] as Map<String, dynamic>? ?? const {},
        fallbackRailName: 'PAYPAL',
      ),
      usdcMisa: RailSimulationResult.fromJson(
        json['usdcMisa'] as Map<String, dynamic>? ?? const {},
        fallbackRailName: 'USDC_MISA',
      ),
      savingsVnd: (json['savingsVnd'] as num?)?.toDouble() ?? 0,
      savingsPercent: (json['savingsPercent'] as num?)?.toDouble() ?? 0,
    );
  }
}
