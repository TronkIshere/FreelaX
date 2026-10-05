package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "review_audits", indexes = @Index(name = "idx_review_audit_review", columnList = "review_id"))
@Getter @Setter
public class ReviewAudit extends AbstractEntity<UUID> {
    @Column(name = "review_id", nullable = false, updatable = false)
    private UUID reviewId;
    @Column(nullable = false, updatable = false)
    private UUID actorId;
    @Column(nullable = false, updatable = false, length = 30)
    private String action;
    @Column(nullable = false, updatable = false, length = 30)
    private String beforeState;
    @Column(nullable = false, updatable = false, length = 30)
    private String afterState;
    @Column(nullable = false, updatable = false, length = 2000)
    private String reason;
    @Column(nullable = false, updatable = false, length = 36)
    private String requestId;
}
