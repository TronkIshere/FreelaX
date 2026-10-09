package com.payment.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "unified_usd_orders_mock")
public class UnifiedUsdOrderMock {
    @Id
    @Column(name = "payment_flow_id")
    private UUID paymentFlowId;
    @Version
    private long version;
    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;
    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;
    @Column(name = "milestone_id", nullable = false, updatable = false, unique = true)
    private UUID milestoneId;
    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;
    @Column(name = "gross_usd", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal grossUsd;
    @Column(name = "escrow_usdc", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal escrowUsdc;
    @Column(name = "payer_bank_code", length = 30, updatable = false)
    private String payerBankCode;
    @Column(name = "payer_bank_account_number", length = 50, updatable = false)
    private String payerBankAccountNumber;
    @Column(name = "payer_bank_account_holder_name", length = 100, updatable = false)
    private String payerBankAccountHolderName;
    @Column(name = "fund_key", nullable = false, length = 100, updatable = false)
    private String fundKey;
    @Column(name = "quote_id", nullable = false, length = 80, updatable = false)
    private String quoteId;
    @Column(name = "quote_expires_at", nullable = false, updatable = false)
    private Instant quoteExpiresAt;
    @Column(name = "status", nullable = false, length = 20)
    private String status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
