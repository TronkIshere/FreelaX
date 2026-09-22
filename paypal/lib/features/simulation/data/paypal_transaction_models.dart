library;

class PaypalFeeBreakdown {
  const PaypalFeeBreakdown({
    required this.commercialFeeUsd,
    required this.fxSpreadCostUsd,
    required this.netUsdAfterFees,
    required this.effectiveFeeRatePercent,
  });

  final double commercialFeeUsd;
  final double fxSpreadCostUsd;
  final double netUsdAfterFees;
  final double effectiveFeeRatePercent;

  factory PaypalFeeBreakdown.fromJson(Map<String, dynamic> json) =>
      PaypalFeeBreakdown(
        commercialFeeUsd: (json['commercialFeeUsd'] as num?)?.toDouble() ?? 0,
        fxSpreadCostUsd: (json['fxSpreadCostUsd'] as num?)?.toDouble() ?? 0,
        netUsdAfterFees: (json['netUsdAfterFees'] as num?)?.toDouble() ?? 0,
        effectiveFeeRatePercent:
            (json['effectiveFeeRatePercent'] as num?)?.toDouble() ?? 0,
      );
}

class PaypalPayoutTransaction {
  const PaypalPayoutTransaction({
    required this.id,
    required this.platformPayoutId,
    required this.payeeId,
    required this.status,
    required this.grossAmountUsd,
    required this.midMarketRate,
    required this.feeBreakdown,
    required this.netVnd,
    this.paymentDate,
    this.withdrawnAt,
  });

  final String id;
  final String platformPayoutId;
  final String payeeId;
  final String status; // "RECEIVED" | "WITHDRAWN"
  final double grossAmountUsd;
  final double midMarketRate;
  final PaypalFeeBreakdown feeBreakdown;
  final double netVnd;
  final String? paymentDate;
  final String? withdrawnAt;

  factory PaypalPayoutTransaction.fromJson(Map<String, dynamic> json) =>
      PaypalPayoutTransaction(
        id: json['id'] as String? ?? '',
        platformPayoutId: json['platformPayoutId'] as String? ?? '',
        payeeId: json['payeeId'] as String? ?? '',
        status: json['status'] as String? ?? '',
        grossAmountUsd: (json['grossAmountUsd'] as num?)?.toDouble() ?? 0,
        midMarketRate: (json['midMarketRate'] as num?)?.toDouble() ?? 0,
        feeBreakdown: PaypalFeeBreakdown.fromJson(
            json['feeBreakdown'] as Map<String, dynamic>? ?? const {}),
        netVnd: (json['netVnd'] as num?)?.toDouble() ?? 0,
        paymentDate: json['paymentDate'] as String?,
        withdrawnAt: json['withdrawnAt'] as String?,
      );
}
