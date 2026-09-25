package com.paypal.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "paypal_payout_releases")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaypalPayoutRelease {

    @Id
    @GeneratedValue
    UUID id;

    @Column(nullable = false, unique = true)
    UUID checkoutOrderId;

    @Column(nullable = false)
    UUID payeeId;

    @Column(nullable = false)
    UUID jobId;

    @Column(nullable = false, precision = 19, scale = 2)
    BigDecimal amountUsd;

    String paypalPayoutBatchId;

    String paypalPayoutItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    PaypalPayoutReleaseStatus status;

    @Column(nullable = false)
    LocalDateTime createdAt;

    LocalDateTime releasedAt;
}