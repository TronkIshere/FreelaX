package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.util.UUID;

/** Append-only evidence reference. A provider or chain lookup must confirm its outcome. */
@Entity
@Immutable
@Getter
@Table(name = "payment_flow_evidence", indexes =
        @Index(name = "idx_payment_flow_evidence_flow", columnList = "payment_flow_id"))
public class PaymentFlowEvidence extends AbstractEntity<UUID> {
    @Column(name = "payment_flow_id", nullable = false)
    private UUID paymentFlowId;
    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;
    @Column(name = "milestone_id", nullable = false)
    private UUID milestoneId;
    @Column(name = "kind", nullable = false, length = 40)
    private String kind;
    @Column(name = "status", nullable = false, length = 20)
    private String status;
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;
    @Column(name = "reference", length = 150)
    private String reference;
    @Column(name = "evidence_source", nullable = false, length = 80)
    private String evidenceSource;
    @Column(name = "amount", precision = 19, scale = 6)
    private BigDecimal amount;
    @Column(name = "currency", length = 10)
    private String currency;
    @Column(name = "actor_id")
    private UUID actorId;
    @Column(name = "note", length = 500)
    private String note;

    protected PaymentFlowEvidence() { }

    public PaymentFlowEvidence(PaymentFlow flow, String kind, String status,
            String idempotencyKey, String reference, String evidenceSource,
            BigDecimal amount, String currency) {
        this.paymentFlowId = flow.getId();
        this.jobId = flow.getJobId();
        this.contractId = flow.getContractId();
        this.milestoneId = flow.getMilestoneId();
        this.kind = kind;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.reference = reference;
        this.evidenceSource = evidenceSource;
        this.amount = amount;
        this.currency = currency;
    }

    /** Admin decision that changes work state; money still moves only through verified steps. */
    public static PaymentFlowEvidence adminAction(PaymentFlow flow, UUID adminId, String kind,
            String reference, String note) {
        PaymentFlowEvidence row = new PaymentFlowEvidence(flow, kind, "CONFIRMED", null,
                reference, "ADMIN_DECISION", null, null);
        row.actorId = adminId;
        row.note = note;
        return row;
    }

    /** Admin review of a reconciliation case. It records a decision; it never moves money. */
    public static PaymentFlowEvidence adminReview(PaymentFlow flow, UUID adminId, String boundary,
            String decision, String note) {
        PaymentFlowEvidence row = new PaymentFlowEvidence(flow, "ADMIN_RECONCILIATION_REVIEW",
                decision, null, boundary, "ADMIN_DECISION", null, null);
        row.actorId = adminId;
        row.note = note;
        return row;
    }
}
