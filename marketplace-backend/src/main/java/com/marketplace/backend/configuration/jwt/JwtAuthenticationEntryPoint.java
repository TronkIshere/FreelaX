package com.marketplace.backend.configuration.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.dto.response.common.ErrorResponse;
import com.marketplace.backend.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(new Date())
                .status(ErrorCode.UNAUTHENTICATED.getCode())
                .error(ErrorCode.UNAUTHENTICATED.getMessage())
                .path(request.getRequestURI())
                .build();

        response.setStatus(ErrorCode.UNAUTHENTICATED.getHttpStatus().value());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
