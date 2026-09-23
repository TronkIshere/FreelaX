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
            HttpHeaders.ACCEPT
    );

    public static final List<String> CORS_ALLOWED_METHODS = Arrays.asList(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.DELETE.name()
    );

    public static final String[] WHITE_LIST = {
            "/api/v1/auth/**",
            "/internal/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**"
    };
}
