package com.marketplace.backend.dto.response.dispute;

import com.marketplace.backend.entity.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AdminDisputeDetail(
        DisputeResponse dispute,
        ContractSnapshot contract,
        List<Requirement> deliverables,
        List<Criterion> acceptanceCriteria,
        List<Submission> submissions,
        FundingStatus fundingStatus,
        List<Audit> audit) {
    public record ContractSnapshot(UUID contractId, UUID clientId, UUID freelancerId,
                                   String title, String description, BigDecimal amount, String currency,
                                   Instant deliveryDueAt, int maxRevisions, int revisionsUsed) {}
    public record Requirement(UUID id, String title, String description, boolean required) {}
    public record Criterion(UUID id, String description, boolean required) {}
    public record Submission(UUID id, int version, String status, String summary, String reviewerFeedback,
                             Instant submittedAt, LocalDateTime reviewedAt, List<SubmissionItem> evidence) {}
    public record SubmissionItem(UUID requirementId, String kind, String description, String url) {}
    public record Audit(UUID actorId, String action, String beforeStatus, String afterStatus,
                        String reason, String requestId, LocalDateTime createdAt) {}
}
