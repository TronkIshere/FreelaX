package com.payment.backend.dto.response.bofa;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BofaPayoutReleaseResponse(
        UUID releaseId, String releaseKey, UUID checkoutOrderId, UUID recipientUserId,
        String status, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String currency, boolean simulation, String releaseReference, boolean retryable,
        Instant createdAt, Instant updatedAt) { }
