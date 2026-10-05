package com.marketplace.backend.dto.response.bofa;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record PaymentRefundResult(UUID refundId, String refundKey, UUID checkoutOrderId, UUID payerUserId,
        String status, BigDecimal amount, String currency, Boolean simulation, String refundReference,
        boolean retryable, Instant createdAt, Instant updatedAt) { }
