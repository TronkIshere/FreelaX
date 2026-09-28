package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "job_submissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "submission_version"}))
public class JobSubmission extends AbstractEntity<UUID> {

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "freelancer_id", nullable = false)
    private UUID freelancerId;

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
}
