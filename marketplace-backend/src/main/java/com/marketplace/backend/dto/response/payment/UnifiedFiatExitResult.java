package com.marketplace.backend.dto.response.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UnifiedFiatExitResult(UUID paymentFlowId, UUID jobId, UUID contractId,
        UUID milestoneId, String kind, String idempotencyKey, String withdrawalReference,
        String beneficiary, BigDecimal grossUsdc, BigDecimal feeUsdc, BigDecimal grossUsd,
        BigDecimal vndRate, BigDecimal payoutVnd, String status, Instant updatedAt,
        boolean simulation) { }
