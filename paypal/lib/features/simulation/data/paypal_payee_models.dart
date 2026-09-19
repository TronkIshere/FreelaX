library;

class PaypalPayee {
  const PaypalPayee({
    required this.id,
    required this.fullName,
    required this.paypalEmail,
    this.phone,
    this.address,
    this.nationality,
    required this.active,
  });

  final String id;
  final String fullName;
  final String paypalEmail;
  final String? phone;
  final String? address;
  final String? nationality;
  final bool active;

  factory PaypalPayee.fromJson(Map<String, dynamic> json) => PaypalPayee(
        id: json['id'] as String? ?? '',
        fullName: json['fullName'] as String? ?? '',
        paypalEmail: json['paypalEmail'] as String? ?? '',
        phone: json['phone'] as String?,
        address: json['address'] as String?,
        nationality: json['nationality'] as String?,
        active: json['active'] as bool? ?? false,
      );
}
