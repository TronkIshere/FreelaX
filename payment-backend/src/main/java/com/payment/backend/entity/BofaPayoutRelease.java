package com.payment.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bofa_payout_releases", uniqueConstraints = {
        @UniqueConstraint(name = "uk_release_checkout", columnNames = "checkout_order_id"),
        @UniqueConstraint(name = "uk_release_key", columnNames = "release_key")})
@Getter
@Setter
public class BofaPayoutRelease {
    @Id @GeneratedValue
    private UUID id;
    @Column(name = "checkout_order_id", nullable = false, updatable = false)
    private UUID checkoutOrderId;
    @Column(nullable = false, updatable = false)
    private UUID recipientUserId;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;
    @Column(name = "release_key", nullable = false, length = 100, updatable = false)
    private String releaseKey;
    @Column(nullable = false, length = 64, updatable = false)
    private String payloadHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BofaPayoutReleaseStatus status;
    @Column(unique = true, length = 64)
    private String releaseReference;
    @Column(nullable = false, updatable = false)
    private boolean simulation;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Column(length = 255)
    private String lastError;
}
