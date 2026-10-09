package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "job_submissions",
        indexes = @Index(name = "idx_submission_review_due", columnList = "status,review_due_at,review_grace_due_at"),
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"job_id", "submission_version"}),
                @UniqueConstraint(name = "uk_submission_actor_key", columnNames = {"freelancer_id", "idempotency_key"})
        })
public class JobSubmission extends AbstractEntity<UUID> {

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "freelancer_id", nullable = false)
    private UUID freelancerId;

    @Column(name = "contract_id")
    private UUID contractId;

    @Column(name = "milestone_id")
    private UUID milestoneId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(length = 64)
    private String payloadHash;

    @Column(name = "submission_version", nullable = false)
    private int version;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(length = 2048)
    private String deliverableUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private JobSubmissionStatus status;

    @Column(columnDefinition = "TEXT")
    private String reviewerFeedback;

    private LocalDateTime reviewedAt;

    private Instant submittedAt;

    private boolean submittedLate;

    private Instant reviewDueAt;

    private int reviewReminderCount;

    private Instant reviewGraceDueAt;

    private boolean reviewedAutomatically;

    @Column(columnDefinition = "TEXT")
    private String reviewCriterionIdsJson;

    @Column(columnDefinition = "TEXT")
    private String reviewDeliverableIdsJson;
}
