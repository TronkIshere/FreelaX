package com.freelax.solanagateway.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GatewayException.class)
    ResponseEntity<ApiError> handleGateway(GatewayException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(new ApiError(
                Instant.now(), exception.status().value(), exception.code() + ": " + exception.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> handleBadRequest(Exception exception, HttpServletRequest request) {
        String message = exception instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream()
                    .findFirst().map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .orElse("Invalid request")
                : exception.getMessage();
        return ResponseEntity.badRequest().body(new ApiError(
                Instant.now(), 400, "INVALID_REQUEST: " + message, request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(
                Instant.now(), 500, "INTERNAL_ERROR", request.getRequestURI()));
    }
}
