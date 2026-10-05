package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ReviewAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewAuditRepository extends JpaRepository<ReviewAudit, UUID> {
    List<ReviewAudit> findByReviewIdOrderByCreatedAtAsc(UUID reviewId);
}
