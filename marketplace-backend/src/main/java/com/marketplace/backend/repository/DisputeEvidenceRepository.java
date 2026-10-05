package com.marketplace.backend.repository;

import com.marketplace.backend.entity.DisputeEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DisputeEvidenceRepository extends JpaRepository<DisputeEvidence, UUID> {
    List<DisputeEvidence> findByDisputeIdOrderByCreatedAtAsc(UUID disputeId);
}
