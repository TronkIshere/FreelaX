package com.marketplace.backend.repository;

import com.marketplace.backend.entity.DisputeAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DisputeAuditRepository extends JpaRepository<DisputeAudit, UUID> {
    List<DisputeAudit> findByDisputeIdOrderByCreatedAtAsc(UUID disputeId);
}
