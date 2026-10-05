package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "contract_settlements", uniqueConstraints = {
        @UniqueConstraint(name = "uk_settlement_milestone", columnNames = "milestone_id"),
        @UniqueConstraint(name = "uk_settlement_release_key", columnNames = "release_key"),
        @UniqueConstraint(name = "uk_settlement_checkout", columnNames = "checkout_order_id")
}, indexes = @Index(name = "idx_settlement_due", columnList = "next_attempt_at"))
public class ContractSettlement extends AbstractEntity<UUID> {
    @Version
    private long version;
    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;
    @Column(name = "milestone_id", nullable = false, updatable = false)
    private UUID milestoneId;
    @Column(nullable = false, updatable = false)
    private UUID jobId;
    @Column(nullable = false, updatable = false)
    private UUID fundingTransactionId;
    @Column(name = "checkout_order_id", nullable = false, updatable = false)
    private UUID checkoutOrderId;
    @Column(nullable = false, updatable = false)
    private UUID freelancerId;
    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, updatable = false, length = 3)
    private String currency;
    @Column(name = "release_key", nullable = false, updatable = false, length = 100)
    private String releaseKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SettlementMoneyStatus moneyStatus = SettlementMoneyStatus.PENDING;
    private UUID paymentReleaseId;
    @Column(length = 128)
    private String paymentReleaseReference;
    private UUID payoutRecordId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SettlementStageStatus onChainStatus = SettlementStageStatus.NOT_STARTED;
    @Column(length = 128)
    private String onChainReference;
    @Column(length = 100)
    private String onChainError;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SettlementStageStatus offRampStatus = SettlementStageStatus.NOT_STARTED;
    @Column(length = 128)
    private String offRampReference;
    @Column(length = 100)
    private String offRampError;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SettlementStageStatus taxStatus = SettlementStageStatus.NOT_STARTED;
    @Column(length = 128)
    private String taxReference;
    @Column(length = 100)
    private String taxError;
    // Step 4.0 is a simulation ledger; never imply an actual bank transfer.
    @Column(nullable = false)
    private boolean simulation = true;
    @Column(length = 100)
    private String lastError;
    private boolean retryable = true;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();
}
