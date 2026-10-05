package com.misa.backend.dto.response.misa;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// Service recovery allowlist: no taxpayer tax/identity data, credentials or signing serial.
public record CertificateRecoveryResponse(UUID certificateId, UUID payoutTransactionId,
        String platformPayoutId, String idempotencyKey, String status,
        String certificateNumber, String symbol, BigDecimal amountUsdc,
        BigDecimal exchangeRate, BigDecimal taxableIncome, BigDecimal taxWithheld,
        String currency, LocalDateTime createdAt, LocalDateTime issuedAt, boolean simulation) {}
