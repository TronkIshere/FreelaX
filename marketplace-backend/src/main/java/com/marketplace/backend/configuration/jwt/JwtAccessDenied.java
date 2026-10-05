package com.marketplace.backend.configuration.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.configuration.RequestCorrelation;
import com.marketplace.backend.dto.response.common.ErrorResponse;
import com.marketplace.backend.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;

@Component
public class JwtAccessDenied implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(new Date())
                .status(ErrorCode.ACCESS_DENIED.getCode())
                .error(ErrorCode.ACCESS_DENIED.getMessage())
                .path(request.getRequestURI())
                .code(ErrorCode.ACCESS_DENIED.name())
                .requestId(RequestCorrelation.id(request))
                .retryable(false)
                .build();

        response.setStatus(ErrorCode.ACCESS_DENIED.getHttpStatus().value());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
