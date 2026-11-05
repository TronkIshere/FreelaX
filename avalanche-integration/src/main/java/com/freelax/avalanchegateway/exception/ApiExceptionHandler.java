package com.freelax.avalanchegateway.exception;

import com.freelax.avalanchegateway.applicationException.ApiException;
import com.freelax.avalanchegateway.applicationException.AvalancheErrorDefinitions;
import com.freelax.avalanchegateway.dto.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static ResponseEntity<ApiError> body(AvalancheErrorDefinitions definition, HttpServletRequest request) {
        return ResponseEntity.status(definition.status())
                .body(new ApiError(Instant.now(), definition.status(), definition.code(), request.getRequestURI()));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e, HttpServletRequest request) {
        return body(e.definition(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException e, HttpServletRequest request) {
        return body(AvalancheErrorDefinitions.INVALID_REQUEST, request);
    }

    @ExceptionHandler(ChainClientException.class)
    ResponseEntity<ApiError> chain(ChainClientException e, HttpServletRequest request) {
        log.warn("chain unavailable: {}", e.getMessage());
        return body(AvalancheErrorDefinitions.CHAIN_UNAVAILABLE, request);
    }

    @ExceptionHandler(UnreadableChainStateException.class)
    ResponseEntity<ApiError> unreadable(UnreadableChainStateException e, HttpServletRequest request) {
        log.warn("chain state unreadable: {}", e.getMessage());
        return body(AvalancheErrorDefinitions.CHAIN_STATE_UNREADABLE, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> other(Exception e, HttpServletRequest request) {
        log.error("unexpected failure: {}", e.getClass().getSimpleName());
        return body(AvalancheErrorDefinitions.INTERNAL_ERROR, request);
    }
}
