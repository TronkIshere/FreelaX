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
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "freelancer_payout_records")
public class FreelancerPayoutRecord extends AbstractEntity<UUID> {

    @Column(nullable = false, unique = true)
    private UUID jobId;

    @Column(nullable = false)
    private UUID freelancerId;

    @Column(nullable = false)
    private UUID clientUserId;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal amountUsd;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal onRampFeeUsd;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal amountUsdNet;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal amountUsdcReceived;

    @Column(nullable = false, length = 20)
    private String onRampPurchaseId;

    @Column(nullable = false, length = 128)
    private String onRampTransactionSignature;

    @Column(length = 64)
    private String onRampClientUsdcAta;

    @Column(length = 64)
    private String onRampReceiptPda;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal usdcToVndRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExchangeRateSource usdcToVndRateSource;

    @Column(nullable = false, precision = 20, scale = 0)
    private BigDecimal amountVndBeforeOffRampFee;

    @Column(nullable = false, precision = 20, scale = 0)
    private BigDecimal offRampFeeVnd;

    @Column(nullable = false, precision = 20, scale = 0)
    private BigDecimal amountVndActual;

    @Column(length = 64)
    private String offRampReference;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal taxUsdToVndRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExchangeRateSource taxRateSource;

    @Column(nullable = false, precision = 20, scale = 0)
    private BigDecimal taxableAmountVnd;

    private UUID misaPayoutTransactionId;

    private UUID misaCertificateId;
}