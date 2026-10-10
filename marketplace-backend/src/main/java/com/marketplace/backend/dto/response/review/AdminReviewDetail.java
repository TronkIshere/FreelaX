package com.marketplace.backend.dto.response.review;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminReviewDetail(ReviewResponse review, List<Audit> audit) {
    public record Audit(UUID actorId, String action, String beforeState, String afterState,
                        String reason, String requestId, Instant createdAt) {}
}
