package com.marketplace.backend.dto.response.auth;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SignInResponse {
    SignInStatus status;
    String accessToken;
    String refreshToken;
    UUID userId;
    String email;
}
