library;

class PaypalCheckoutOrder {
  const PaypalCheckoutOrder({
    required this.id,
    required this.referenceId,
    required this.amountUsd,
    required this.paypalOrderId,
    this.paypalCaptureId,
    required this.status,
    this.approvalUrl,
    this.createdAt,
    this.capturedAt,
  });

  final String id;
  final String referenceId;
  final double amountUsd;
  final String paypalOrderId;
  final String? paypalCaptureId;
  final String status;
  final String? approvalUrl;
  final String? createdAt;
  final String? capturedAt;

  factory PaypalCheckoutOrder.fromJson(Map<String, dynamic> json) =>
      PaypalCheckoutOrder(
        id: json['id'] as String? ?? '',
        referenceId: json['referenceId'] as String? ?? '',
        amountUsd: (json['amountUsd'] as num?)?.toDouble() ?? 0,
        paypalOrderId: json['paypalOrderId'] as String? ?? '',
        paypalCaptureId: json['paypalCaptureId'] as String?,
        status: json['status'] as String? ?? '',
        approvalUrl: json['approvalUrl'] as String?,
        createdAt: json['createdAt'] as String?,
        capturedAt: json['capturedAt'] as String?,
      );
}
