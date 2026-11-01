package com.freelax.avalanchegateway.api;

import com.freelax.avalanchegateway.chain.ChainClientException;
import com.freelax.avalanchegateway.tx.NonceGapException;
import com.freelax.avalanchegateway.tx.SubmissionService;
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

    private static ResponseEntity<ApiError> body(int status, String code, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status, code, request.getRequestURI()));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e, HttpServletRequest request) {
        return body(e.status(), e.code(), request);
    }

    @ExceptionHandler({SubmissionService.InvalidRequestException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> invalid(Exception e, HttpServletRequest request) {
        return body(400, "INVALID_REQUEST", request);
    }

    @ExceptionHandler(SubmissionService.IdempotencyConflictException.class)
    ResponseEntity<ApiError> conflict(Exception e, HttpServletRequest request) {
        return body(409, "IDEMPOTENCY_CONFLICT", request);
    }

    @ExceptionHandler({SubmissionService.SignerUnavailableException.class, NonceGapException.class, SubmissionService.FeeCapExceededException.class})
    ResponseEntity<ApiError> unavailable(Exception e, HttpServletRequest request) {
        return body(409, e.getMessage().substring(0, e.getMessage().indexOf(':')), request);
    }

    @ExceptionHandler(ChainClientException.class)
    ResponseEntity<ApiError> chain(ChainClientException e, HttpServletRequest request) {
        log.warn("chain unavailable: {}", e.getMessage());
        return body(502, "CHAIN_UNAVAILABLE", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> other(Exception e, HttpServletRequest request) {
        log.error("unexpected failure: {}", e.getClass().getSimpleName());
        return body(500, "INTERNAL_ERROR", request);
    }
}
