package com.payment.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partner_statement_mock", uniqueConstraints = @UniqueConstraint(name = "uk_partner_statement_event", columnNames = "event_key"))
@Getter @Setter
public class PartnerStatementMock {
    @Id @GeneratedValue private UUID id;
    @Column(name = "milestone_id", nullable = false, updatable = false)
    private UUID milestoneId;
    @Column(name = "event_key", nullable = false, length = 120, updatable = false)
    private String eventKey;
    @Column(nullable = false, length = 20, updatable = false)
    private String kind;
    @Column(name = "delta_usd", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal deltaUsd;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
