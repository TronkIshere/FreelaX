package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "payment_flow_steps", uniqueConstraints =
        @UniqueConstraint(name = "uk_payment_flow_step", columnNames = {"payment_flow_id", "kind"}))
public class PaymentFlowStep extends AbstractEntity<UUID> {
    @Version
    private long version;
    @Column(name = "payment_flow_id", nullable = false)
    private UUID paymentFlowId;
    @Column(nullable = false, length = 40)
    private String kind;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(precision = 19, scale = 6)
    private BigDecimal amount;
    @Column(length = 10)
    private String currency;
    @Column(name = "provider", length = 80)
    private String provider;
    @Column(name = "reference", length = 150)
    private String reference;
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;
    @Column(name = "transaction_signature", length = 120)
    private String transactionSignature;
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
    @Column(name = "account_address", length = 100)
    private String accountAddress;
    @Column(name = "token_account", length = 100)
    private String tokenAccount;
    @Column(name = "build_session_id", length = 120)
    private String buildSessionId;
    @Column(name = "beneficiary", length = 200)
    private String beneficiary;
    @Column(name = "rate_id", length = 30)
    private String rateId;
    @Column(name = "quote_expires_at")
    private Instant quoteExpiresAt;
    @Column(name = "vnd_rate", precision = 19, scale = 2)
    private BigDecimal vndRate;
    @Column(name = "payout_vnd", precision = 19, scale = 0)
    private BigDecimal payoutVnd;
    @Column(name = "fee_usdc", precision = 19, scale = 6)
    private BigDecimal feeUsdc;
    @Column(name = "evidence_source", length = 80)
    private String evidenceSource;
    @Column(name = "retry_after")
    private Instant retryAfter;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}
