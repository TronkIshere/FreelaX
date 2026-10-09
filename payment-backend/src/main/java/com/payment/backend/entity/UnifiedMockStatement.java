package com.payment.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Getter
@Setter
@Table(name = "unified_mock_statements")
public class UnifiedMockStatement {
    @Id
    @Column(name = "event_key", length = 120)
    private String eventKey;
    @Column(name = "payment_flow_id", nullable = false)
    private UUID paymentFlowId;
    @Column(name = "kind", nullable = false, length = 30)
    private String kind;
    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;
    @Column(name = "reference", nullable = false, length = 120)
    private String reference;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
