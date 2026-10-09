package com.marketplace.backend.dto.response.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UnifiedUsdOrderResult(UUID paymentFlowId, UUID jobId, UUID contractId,
        UUID milestoneId, UUID clientId, BigDecimal grossUsd, BigDecimal escrowUsdc,
        String payerBankCode, String payerBankAccountNumber, String payerBankAccountHolderName,
        String fundKey, String quoteId, String quoteSource, Instant quoteExpiresAt,
        String status, Instant updatedAt, boolean simulation) { }
