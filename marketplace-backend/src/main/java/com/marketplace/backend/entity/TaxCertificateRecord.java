package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tax_certificate_records")
public class TaxCertificateRecord extends AbstractEntity<UUID> {

    @Column(nullable = false, unique = true)
    private UUID jobId;

    @Column(nullable = false)
    private UUID freelancerId;

    @Column(nullable = false)
    private UUID clientUserId;

    private UUID payoutRecordId;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal amountUsd;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal usdToVndRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExchangeRateSource rateSource;

    @Column(nullable = false, precision = 20, scale = 0)
    private BigDecimal taxableIncomeVnd;

    @Column(precision = 20, scale = 0)
    private BigDecimal taxWithheldVnd;

    @Column(length = 160)
    private String transactionReference;

    private UUID misaTaxpayerId;

    private UUID misaPayoutTransactionId;

    @Column(unique = true)
    private UUID misaCertificateId;

    @Column(length = 64)
    private String certificateNumber;

    @Column(length = 32)
    private String certificateSymbol;

    @Column(length = 64)
    private String lookupCode;

    @Column(length = 128)
    private String submissionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaxCertificateStatus status = TaxCertificateStatus.PENDING_EXPORT;

    @Column(length = 40)
    private String misaStatusRaw;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    private LocalDateTime issuedAt;

    private LocalDateTime submittedAt;

    private LocalDateTime lastSyncedAt;
}