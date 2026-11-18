class LoginResponse {
  final String accessToken;
  final String tokenType;

  const LoginResponse({
    required this.accessToken,
    required this.tokenType,
  });

  factory LoginResponse.fromJson(Map<String, dynamic> json) {
    return LoginResponse(
      accessToken: json['access_token']?.toString() ?? '',
      tokenType: json['token_type']?.toString() ?? 'bearer',
    );
  }
}
