package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "contract_disputes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dispute_contract", columnNames = "contract_id"),
                @UniqueConstraint(name = "uk_dispute_refund_key", columnNames = "refund_key")
        },
        indexes = {
                @Index(name = "idx_dispute_contract_status", columnList = "contract_id,status"),
                @Index(name = "idx_dispute_refund_due", columnList = "status,next_attempt_at")
        })
public class ContractDispute extends AbstractEntity<UUID> {
    @Version
    private long version;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    private UUID jobId;
    private UUID milestoneId;

    @Column(name = "submission_id")
    private UUID submissionId;

    @Column(name = "opened_by", nullable = false)
    private UUID openedBy;

    @Column(nullable = false, length = 60)
    private String reasonCode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    private Instant openedAt;
    private Instant negotiationUntil;
    private Instant moderationDueAt;
    private UUID negotiationProposedBy;
    @Column(length = 32)
    private String negotiationOutcome;
    @Column(length = 2000)
    private String negotiationReason;
    private Instant negotiationProposedAt;
    private UUID claimedBy;
    private Instant claimedAt;
    private UUID resolvedBy;
    private Instant decisionAt;
    private Instant resolvedAt;
    @Column(length = 2000)
    private String resolutionReason;
    @Column(length = 100)
    private String resolutionKey;
    @Column(length = 64)
    private String resolutionHash;

    // Immutable financial claim captured before the first Step 5 refund attempt.
    private UUID fundingTransactionId;
    private UUID checkoutOrderId;
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(length = 3)
    private String currency;
    @Column(length = 100)
    private String refundKey;
    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private SettlementMoneyStatus refundStatus;
    private UUID paymentRefundId;
    @Column(length = 128)
    private String refundReference;
    @Column(length = 100)
    private String lastError;
    private boolean retryable;
    private Instant nextAttemptAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DisputeStatus status;
}
