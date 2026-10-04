package com.marketplace.backend.repository;

import com.marketplace.backend.entity.SubmissionEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubmissionEvidenceRepository extends JpaRepository<SubmissionEvidence, UUID> {
    List<SubmissionEvidence> findBySubmissionIdOrderByCreatedAtAsc(UUID submissionId);
}
