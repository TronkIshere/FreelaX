package com.payment.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partner_escrow_mock", indexes = @Index(name = "idx_partner_escrow_status", columnList = "status,created_at"))
@Getter @Setter
public class PartnerEscrowMock {
    @Id
    @Column(name = "milestone_id", nullable = false)
    private UUID milestoneId;
    @Version private long version;
    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;
    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;
    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;
    @Column(name = "freelancer_id", nullable = false, updatable = false)
    private UUID freelancerId;
    @Column(name = "gross_usd", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal grossUsd;
    @Column(name = "fund_key", nullable = false, length = 100, updatable = false)
    private String fundKey;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(name = "release_key", length = 100)
    private String releaseKey;
    @Column(name = "refund_key", length = 100)
    private String refundKey;
    @Column(name = "fee_usd", precision = 19, scale = 2)
    private BigDecimal feeUsd;
    @Column(name = "freelancer_usd", precision = 19, scale = 2)
    private BigDecimal freelancerUsd;
    @Column(name = "usd_vnd_rate", precision = 19, scale = 6)
    private BigDecimal usdVndRate;
    @Column(name = "payout_vnd", precision = 19, scale = 0)
    private BigDecimal payoutVnd;
    @Column(name = "rate_locked_at")
    private Instant rateLockedAt;
    @Column(name = "recipient_bank_code", length = 30)
    private String recipientBankCode;
    @Column(name = "recipient_bank_last4", length = 4)
    private String recipientBankLast4;
    @Column(name = "recipient_bank_hash", length = 64)
    private String recipientBankHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
