class AuthUser {
  const AuthUser({
    required this.id,
    required this.email,
    this.displayName,
    this.accessToken,
    this.refreshToken,
  });

  final String id;
  final String email;
  final String? displayName;
  final String? accessToken;
  final String? refreshToken;

  factory AuthUser.fromApiJson(Map<String, dynamic> json) {
    return AuthUser(
      id: (json['userId'] ?? json['id'] ?? '') as String,
      email: json['email'] as String? ?? '',
      displayName: json['displayName'] as String?,
      accessToken: json['accessToken'] as String?,
      refreshToken: json['refreshToken'] as String?,
    );
  }

  factory AuthUser.fromJson(Map<String, dynamic> json) {
    return AuthUser(
      id: json['id'] as String? ?? '',
      email: json['email'] as String? ?? '',
      displayName: json['displayName'] as String?,
      accessToken: json['accessToken'] as String?,
      refreshToken: json['refreshToken'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'email': email,
      'displayName': displayName,
      'accessToken': accessToken,
      'refreshToken': refreshToken,
    };
  }
}
