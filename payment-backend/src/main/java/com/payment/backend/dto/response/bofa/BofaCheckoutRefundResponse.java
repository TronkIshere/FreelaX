package com.payment.backend.dto.response.bofa;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record BofaCheckoutRefundResponse(UUID refundId, String refundKey, UUID checkoutOrderId, UUID payerUserId,
        String status, @JsonSerialize(using = ToStringSerializer.class) BigDecimal amount, String currency,
        boolean simulation, String refundReference, boolean retryable, Instant createdAt, Instant updatedAt) { }
