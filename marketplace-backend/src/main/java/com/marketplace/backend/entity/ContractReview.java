package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contract_reviews", uniqueConstraints = @UniqueConstraint(name = "uk_review_contract_reviewer",
        columnNames = {"contract_id", "reviewer_id"}), indexes = {
        @Index(name = "idx_review_reviewee_public", columnList = "reviewee_id,published_at,invalidated_at"),
        @Index(name = "idx_review_due", columnList = "completed_at,published_at")
})
@Getter @Setter
public class ContractReview extends AbstractEntity<UUID> {
    @Version private long version;
    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;
    @Column(nullable = false, updatable = false)
    private UUID jobId;
    @Column(name = "reviewer_id", nullable = false, updatable = false)
    private UUID reviewerId;
    @Column(name = "reviewee_id", nullable = false, updatable = false)
    private UUID revieweeId;
    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;
    private Integer overall;
    private Integer communication;
    private Integer requirementsOrQuality;
    private Integer timeliness;
    @Column(length = 2000)
    private String comment;
    private Instant submittedAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    private Instant reportedAt;
    private UUID reportedBy;
    private Instant hiddenAt;
    private Instant invalidatedAt;
}
