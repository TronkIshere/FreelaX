package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "submission_evidence", indexes = @Index(name = "idx_evidence_submission", columnList = "submission_id"))
public class SubmissionEvidence extends AbstractEntity<UUID> {
    public enum Kind { DELIVERABLE, ACCEPTANCE_CRITERION }

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "requirement_id", nullable = false)
    private UUID requirementId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Kind kind;

    @Column(length = 2000)
    private String description;

    @Column(length = 2048)
    private String url;
}
