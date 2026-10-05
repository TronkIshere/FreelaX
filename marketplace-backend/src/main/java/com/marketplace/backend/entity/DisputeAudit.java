package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "dispute_audit", indexes = @Index(name = "idx_dispute_audit", columnList = "dispute_id"))
public class DisputeAudit extends AbstractEntity<UUID> {
    @Column(name = "dispute_id", nullable = false, updatable = false)
    private UUID disputeId;
    @Column(nullable = false, updatable = false)
    private UUID actorId;
    @Column(nullable = false, updatable = false, length = 32)
    private String action;
    @Column(updatable = false, length = 32)
    private String beforeStatus;
    @Column(nullable = false, updatable = false, length = 32)
    private String afterStatus;
    @Column(updatable = false, length = 2000)
    private String reason;
    @Column(updatable = false, length = 100)
    private String requestId;
    @Column(updatable = false, length = 100)
    private String idempotencyKey;
}
