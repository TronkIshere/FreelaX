package com.freelax.solanagateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freelax.solanagateway.exception.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");
    private final SolanaProperties properties;
    private final ObjectMapper objectMapper;

    public InternalApiKeyFilter(SolanaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/solana/")
                || !MUTATING_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String configured = properties.internalApiKey();
        String supplied = request.getHeader("X-Internal-Api-Key");
        if (configured == null || configured.isBlank() || !constantTimeEquals(configured, supplied)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    new ApiError(Instant.now(), 401, "INVALID_INTERNAL_API_KEY", request.getRequestURI()));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        if (supplied == null) {
            return false;
        }
        int diff = expected.length() ^ supplied.length();
        int max = Math.max(expected.length(), supplied.length());
        for (int i = 0; i < max; i++) {
            char left = i < expected.length() ? expected.charAt(i) : 0;
            char right = i < supplied.length() ? supplied.charAt(i) : 0;
            diff |= left ^ right;
        }
        return diff == 0;
    }
}
