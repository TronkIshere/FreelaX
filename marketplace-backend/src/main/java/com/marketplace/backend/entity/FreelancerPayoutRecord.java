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
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "freelancer_payout_records")
public class FreelancerPayoutRecord extends AbstractEntity<UUID> {

    // Contract records are driven exclusively by SettlementDownstreamService.
    @Column(unique = true)
    private UUID contractSettlementId;

    @Column(nullable = false, unique = true)
    private UUID jobId;

    @Column(nullable = false)
    private UUID freelancerId;

    @Column(nullable = false)
    private UUID clientUserId;

    @Column(nullable = false)
    private boolean simulated = true;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal amountUsd;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal onRampFeeUsd;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal amountUsdNet;

    @Column(nullable = false, length = 20)
    private String onRampUsdAmountE6;

    @Column(nullable = false, length = 20)
    private String onRampPurchaseId;

    @Column(length = 64)
    private String onRampClientPublicKey;

    @Column(nullable = false, length = 20)
    private String onRampNetwork;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OnRampStatus onRampStatus = OnRampStatus.NOT_STARTED;

    @Column(length = 128)
    private String onRampTransactionSignature;

    @Column(length = 64)
    private String onRampClientUsdcAta;

    @Column(length = 64)
    private String onRampReceiptPda;

    private LocalDateTime onRampSubmittedAt;

    private LocalDateTime onRampConfirmedAt;

    @Column(columnDefinition = "TEXT")
    private String onRampError;

    @Column(precision = 20, scale = 6)
    private BigDecimal amountUsdcReceived;

    @Column(length = 64)
    private String freelancerPublicKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ClientPaymentStatus clientPaymentStatus = ClientPaymentStatus.NOT_STARTED;

    @Column(length = 20)
    private String rateId;

    @Column(length = 128)
    private String rateTransactionSignature;

    @Column(length = 64)
    private String rateSnapshotPda;

    @Column(length = 20)
    private String invoiceId;

    private Long invoiceExpiresAtEpoch;

    @Column(length = 64)
    private String invoicePda;

    @Column(length = 64)
    private String paymentMint;

    @Column(length = 128)
    private String invoiceTransactionSignature;

    @Column(length = 128)
    private String paymentTransactionSignature;

    private LocalDateTime clientPaymentSubmittedAt;

    private LocalDateTime clientPaymentConfirmedAt;

    @Column(columnDefinition = "TEXT")
    private String clientPaymentError;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OnChainOffRampStatus onChainOffRampStatus = OnChainOffRampStatus.NOT_STARTED;

    @Column(length = 20)
    private String withdrawalId;

    @Column(length = 64)
    private String withdrawalPda;

    @Column(length = 64)
    private String treasuryPublicKey;

    @Column(length = 64)
    private String treasuryUsdcAta;

    @Column(length = 20)
    private String withdrawalTokenAmount;

    @Column(length = 32)
    private String withdrawalFiatAmountVnd;

    @Column(length = 128)
    private String withdrawalTransactionSignature;

    private LocalDateTime withdrawalSubmittedAt;

    private LocalDateTime withdrawalConfirmedAt;

    @Column(columnDefinition = "TEXT")
    private String onChainOffRampError;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OffRampStatus offRampStatus = OffRampStatus.NOT_STARTED;

    @Column(precision = 20, scale = 2)
    private BigDecimal usdcToVndRate;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ExchangeRateSource usdcToVndRateSource;

    private Instant usdcToVndRateObservedAt;

    @Column(precision = 20, scale = 0)
    private BigDecimal amountVndBeforeOffRampFee;

    @Column(precision = 20, scale = 0)
    private BigDecimal offRampFeeVnd;

    @Column(precision = 20, scale = 0)
    private BigDecimal amountVndEstimated;

    @Column(length = 64)
    private String offRampReference;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private BankCode payoutBankCode;

    @Column(length = 64)
    private String payoutBankAccountNumber;

    @Column(length = 255)
    private String payoutBankAccountHolderName;

    private LocalDateTime simulatedPayoutAt;

    @Column(length = 128)
    private String offRampCompletionSignature;

    private LocalDateTime offRampCompletionSubmittedAt;

    private LocalDateTime offRampCompletedAt;

    @Column(columnDefinition = "TEXT")
    private String offRampError;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal taxUsdToVndRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExchangeRateSource taxRateSource;

    private Instant taxRateObservedAt;

    @Column(nullable = false, precision = 20, scale = 0)
    private BigDecimal taxableAmountVnd;

    private UUID misaPayoutTransactionId;

    private UUID misaCertificateId;
}
