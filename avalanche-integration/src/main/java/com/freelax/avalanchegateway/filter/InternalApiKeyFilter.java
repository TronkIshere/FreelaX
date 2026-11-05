package com.freelax.avalanchegateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freelax.avalanchegateway.applicationException.AvalancheErrorDefinitions;
import com.freelax.avalanchegateway.config.AvalancheProperties;
import com.freelax.avalanchegateway.dto.response.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Set;

/** Same protection as the Solana gateway: every mutating call needs the shared internal key; a blank key refuses all of them. */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final Set<String> MUTATING = Set.of("POST", "PUT", "DELETE", "PATCH");

    private final AvalancheProperties properties;
    private final ObjectMapper objectMapper;

    public InternalApiKeyFilter(AvalancheProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/avalanche/") || !MUTATING.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String configured = properties.internalApiKey();
        String supplied = request.getHeader("X-Internal-Api-Key");
        if (configured == null || configured.isBlank() || supplied == null
                || !MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            AvalancheErrorDefinitions definition = AvalancheErrorDefinitions.INVALID_INTERNAL_API_KEY;
            response.setStatus(definition.status());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), definition.status(), definition.code(), request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }
}
