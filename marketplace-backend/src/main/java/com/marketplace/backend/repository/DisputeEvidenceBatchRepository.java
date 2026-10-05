package com.marketplace.backend.repository;

import com.marketplace.backend.entity.DisputeEvidenceBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DisputeEvidenceBatchRepository extends JpaRepository<DisputeEvidenceBatch, UUID> {
    Optional<DisputeEvidenceBatch> findByDisputeIdAndActorIdAndIdempotencyKey(
            UUID disputeId, UUID actorId, String idempotencyKey);
}
