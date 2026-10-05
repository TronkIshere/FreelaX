package com.payment.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Append-only simulated USD entitlement; not a bank transfer or off-ramp. */
@Entity
@Table(name = "bofa_recipient_credits", indexes =
        @Index(name = "idx_credit_recipient_currency", columnList = "recipient_user_id,currency"),
        uniqueConstraints = @UniqueConstraint(name = "uk_credit_checkout", columnNames = "checkout_order_id"))
@Getter
@Setter
public class BofaRecipientCredit {
    @Id
    private UUID releaseId;
    @Column(name = "checkout_order_id", nullable = false, updatable = false)
    private UUID checkoutOrderId;
    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UUID recipientUserId;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
