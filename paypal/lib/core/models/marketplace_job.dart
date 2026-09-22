class MarketplaceJob {
  const MarketplaceJob({
    required this.id,
    required this.title,
    required this.description,
    required this.budgetUsd,
    this.clientUserId,
    this.status,
    this.createdAt,
    this.checkoutOrderId,
  });

  final String id;
  final String title;
  final String description;
  final double budgetUsd;
  final String? clientUserId;
  final String? status;
  final DateTime? createdAt;
  final String? checkoutOrderId;

  factory MarketplaceJob.fromJson(Map<String, dynamic> json) {
    return MarketplaceJob(
      id: json['id'] as String? ?? '',
      title: json['title'] as String? ?? '',
      description: json['description'] as String? ?? '',
      budgetUsd: (json['budgetUsd'] as num?)?.toDouble() ?? 0,
      clientUserId: json['clientUserId'] as String?,
      status: json['status'] as String?,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'] as String)
          : null,
      checkoutOrderId: json['checkoutOrderId'] as String?,
    );
  }

  MarketplaceJob copyWith({String? checkoutOrderId, String? status}) {
    return MarketplaceJob(
      id: id,
      title: title,
      description: description,
      budgetUsd: budgetUsd,
      clientUserId: clientUserId,
      status: status ?? this.status,
      createdAt: createdAt,
      checkoutOrderId: checkoutOrderId ?? this.checkoutOrderId,
    );
  }
}
