package com.marketplace.backend.dto.response.misa;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MisaCertificateRecoveryResult(UUID certificateId, UUID payoutTransactionId,
        String platformPayoutId, String idempotencyKey, String status,
        String certificateNumber, String symbol, BigDecimal amountUsdc,
        BigDecimal exchangeRate, BigDecimal taxableIncome, BigDecimal taxWithheld,
        String currency, LocalDateTime createdAt, LocalDateTime issuedAt, boolean simulation) {}
