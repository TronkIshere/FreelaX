class MarketplaceJob {
  const MarketplaceJob({
    required this.id,
    required this.title,
    required this.description,
    required this.budgetUsd,
    this.clientUserId,
    this.status,
    this.createdAt,
  });

  final String id;
  final String title;
  final String description;
  final double budgetUsd;
  final String? clientUserId;
  final String? status;
  final DateTime? createdAt;

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
    );
  }
}
