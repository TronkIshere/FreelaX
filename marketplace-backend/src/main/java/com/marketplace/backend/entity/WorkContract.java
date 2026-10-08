package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "work_contracts", uniqueConstraints = @UniqueConstraint(columnNames = "job_id"))
public class WorkContract extends AbstractEntity<UUID> {
    @Version
    private long version;
    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @Column(name = "client_user_id", nullable = false)
    private UUID clientUserId;
    @Column(name = "freelancer_id", nullable = false)
    private UUID freelancerId;
    @Column(nullable = false)
    private String titleSnapshot;
    @Column(columnDefinition = "TEXT")
    private String descriptionSnapshot;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal budgetUsd;
    private Instant deliveryDueAt;
    @Column(nullable = false)
    private int reviewWindowHours;
    @Column(nullable = false)
    private int maxRevisions;
    @Column(nullable = false)
    private int revisionsUsed;
    @Column(name = "payment_rail", length = 30)
    private String paymentRail;
    @Column(name = "funding_reminder_sent_at")
    private Instant fundingReminderSentAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContractStatus status;
}
