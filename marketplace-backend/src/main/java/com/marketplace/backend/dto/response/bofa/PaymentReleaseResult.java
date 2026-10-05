package com.marketplace.backend.dto.response.bofa;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentReleaseResult(UUID releaseId, String releaseKey, UUID checkoutOrderId,
                                    UUID recipientUserId, String status, BigDecimal amount,
                                    String currency, Boolean simulation, String releaseReference,
                                    boolean retryable, Instant createdAt, Instant updatedAt) {}
