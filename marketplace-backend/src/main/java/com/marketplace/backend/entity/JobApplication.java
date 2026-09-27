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

import java.util.UUID;

@Entity
@Table(name = "job_applications",
        uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "freelancer_id"}))
@Getter
@Setter
public class JobApplication extends AbstractEntity<UUID> {

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "freelancer_id", nullable = false)
    private UUID freelancerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobApplicationStatus status;
}