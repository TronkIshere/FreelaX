package com.payment.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Getter @Setter
@Table(name = "bofa_checkout_refunds", uniqueConstraints = {
        @UniqueConstraint(name = "uk_refund_checkout", columnNames = "checkout_order_id"),
        @UniqueConstraint(name = "uk_refund_key", columnNames = "refund_key")})
public class BofaCheckoutRefund {
    @Id @GeneratedValue private UUID id;
    @Column(name = "checkout_order_id", nullable = false, updatable = false) private UUID checkoutOrderId;
    @Column(nullable = false, updatable = false) private UUID payerUserId;
    @Column(name = "refund_key", nullable = false, updatable = false, length = 100) private String refundKey;
    @Column(nullable = false, updatable = false, length = 64) private String payloadHash;
    @Column(nullable = false, updatable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, updatable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private BofaRefundStatus status;
    @Column(unique = true, length = 64) private String refundReference;
    @Column(nullable = false, updatable = false) private boolean simulation = true;
    @Column(length = 100) private String lastError;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
}
