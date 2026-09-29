package com.marketplace.backend.dto.response.tax;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TaxCertificateResponse {
    UUID id;
    UUID jobId;
    String jobTitle;
    UUID freelancerId;
    UUID clientUserId;
    String status;
    String statusLabel;
    BigDecimal amountUsd;
    BigDecimal usdToVndRate;
    String rateSource;
    Instant rateObservedAt;
    BigDecimal taxableIncomeVnd;
    BigDecimal taxWithheldVnd;
    String certificateNumber;
    String certificateSymbol;
    String lookupCode;
    UUID misaCertificateId;
    UUID misaPayoutTransactionId;
    String transactionReference;
    String submissionId;
    String taxAuthorityReference;
    LocalDateTime issuedAt;
    LocalDateTime submittedAt;
    LocalDateTime lastSyncedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
