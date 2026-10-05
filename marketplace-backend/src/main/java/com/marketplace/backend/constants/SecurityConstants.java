package com.marketplace.backend.constants;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.util.Arrays;
import java.util.List;

public final class SecurityConstants {

    private SecurityConstants() {
    }

    public static final List<String> CORS_ALLOWED_HEADERS = Arrays.asList(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.ACCEPT,
            "Idempotency-Key"
    );

    public static final List<String> CORS_ALLOWED_METHODS = Arrays.asList(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name()
    );

    public static final String[] WHITE_LIST = {
            "/api/v1/auth/register",
            "/api/v1/auth/sign-in",
            "/api/v1/auth/refresh-token",
            "/api/v1/auth/sign-out",
            "/api/v1/auth/forgot-password/send-otp",
            "/api/v1/auth/forgot-password/verify-otp",
            "/api/v1/auth/forgot-password/reset",
            "/internal/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**"
    };
}
