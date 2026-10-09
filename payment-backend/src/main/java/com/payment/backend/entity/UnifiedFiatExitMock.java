package com.payment.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Mock provider obligation, separate from the Marketplace payment flow. */
@Entity
@Table(name = "unified_fiat_exits")
@Getter
@Setter
public class UnifiedFiatExitMock {
    @Id private UUID paymentFlowId;
    @Column(nullable = false) private UUID jobId;
    @Column(nullable = false) private UUID contractId;
    @Column(nullable = false) private UUID milestoneId;
    @Column(nullable = false, length = 20) private String kind;
    @Column(nullable = false, length = 100) private String idempotencyKey;
    @Column(nullable = false, length = 120) private String withdrawalReference;
    @Column(nullable = false, length = 200) private String beneficiary;
    @Column(nullable = false, precision = 19, scale = 6) private BigDecimal grossUsdc;
    @Column(nullable = false, precision = 19, scale = 6) private BigDecimal feeUsdc;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal grossUsd;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal vndRate;
    @Column(nullable = false, precision = 19, scale = 0) private BigDecimal payoutVnd;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
}
