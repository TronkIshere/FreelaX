package com.marketplace.backend.repository;

import com.marketplace.backend.entity.JobSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobSubmissionRepository extends JpaRepository<JobSubmission, UUID> {
    List<JobSubmission> findByJobIdOrderByVersionAsc(UUID jobId);
    Optional<JobSubmission> findFirstByJobIdOrderByVersionDesc(UUID jobId);
    long countByJobId(UUID jobId);
}
