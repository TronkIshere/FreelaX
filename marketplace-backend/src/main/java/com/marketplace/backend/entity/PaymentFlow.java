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
import java.util.UUID;

/** One flow per milestone. Amounts and terms are a snapshot, never browser supplied. */
@Entity
@Getter
@Setter
@Table(name = "payment_flows", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_flow_milestone", columnNames = "milestone_id"),
        @UniqueConstraint(name = "uk_payment_flow_contract", columnNames = "contract_id")
})
public class PaymentFlow extends AbstractEntity<UUID> {
    public static final String RAIL = "UNIFIED_USDC_PAYOUT";
    public static final int REVIEW_WINDOW_HOURS = 72;

    @Version
    private long version;
    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;
    @Column(name = "milestone_id", nullable = false)
    private UUID milestoneId;
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    @Column(name = "freelancer_id", nullable = false)
    private UUID freelancerId;
    @Column(name = "gross_usd", nullable = false, precision = 19, scale = 2)
    private BigDecimal grossUsd;
    @Column(name = "escrow_usdc", nullable = false, precision = 19, scale = 6)
    private BigDecimal escrowUsdc;
    @Column(name = "platform_fee_usd", nullable = false, precision = 19, scale = 2)
    private BigDecimal platformFeeUsd;
    @Column(name = "payer_bank_code", length = 30)
    private String payerBankCode;
    @Column(name = "payer_bank_account_number", length = 50)
    private String payerBankAccountNumber;
    @Column(name = "payer_bank_account_holder_name", length = 100)
    private String payerBankAccountHolderName;
    @Column(name = "network", length = 30)
    private String network;
    @Column(name = "mint", length = 64)
    private String mint;
    @Column(name = "quote_source", length = 80)
    private String quoteSource;
    @Column(name = "quote_expires_at")
    private Instant quoteExpiresAt;
    @Column(name = "funding_expires_at")
    private Instant fundingExpiresAt;
    @Column(name = "delivery_due_at")
    private Instant deliveryDueAt;
    @Column(name = "review_window_hours", nullable = false)
    private int reviewWindowHours;
    @Column(name = "max_revisions", nullable = false)
    private int maxRevisions;
    @Column(name = "terms_version", nullable = false)
    private int termsVersion;
}
