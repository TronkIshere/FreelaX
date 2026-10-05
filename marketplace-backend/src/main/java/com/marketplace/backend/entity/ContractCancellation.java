package com.marketplace.backend.entity;
import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Getter @Setter
@Table(name = "contract_cancellations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cancellation_contract", columnNames = "contract_id"),
        @UniqueConstraint(name = "uk_cancellation_milestone", columnNames = "milestone_id"),
        @UniqueConstraint(name = "uk_cancellation_refund_key", columnNames = "refund_key")},
        indexes = @Index(name = "idx_cancellation_due", columnList = "retryable,next_attempt_at"))
public class ContractCancellation extends AbstractEntity<UUID> {
    @Version private long version;
    @Column(name = "contract_id", nullable = false, updatable = false) private UUID contractId;
    @Column(name = "milestone_id", nullable = false, updatable = false) private UUID milestoneId;
    @Column(nullable = false, updatable = false) private UUID jobId;
    @Column(nullable = false, updatable = false) private UUID requestedBy;
    @Column(nullable = false, updatable = false, length = 60) private String reasonCode;
    @Column(nullable = false, updatable = false, length = 2000) private String reason;
    @Column(nullable = false, updatable = false, length = 64) private String intentHash;
    private UUID decidedBy;
    private Instant decidedAt;
    @Column(updatable = false) private UUID fundingTransactionId;
    @Column(updatable = false) private UUID checkoutOrderId;
    @Column(precision = 19, scale = 2, updatable = false) private BigDecimal amount;
    @Column(length = 3, updatable = false) private String currency;
    @Column(name = "refund_key", length = 100) private String refundKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private CancellationStatus status;
    @Enumerated(EnumType.STRING) @Column(length = 24) private SettlementMoneyStatus refundStatus;
    private UUID paymentRefundId;
    @Column(length = 128) private String refundReference;
    private boolean simulation = true;
    @Column(length = 100) private String lastError;
    private boolean retryable;
    private Instant nextAttemptAt;
}
