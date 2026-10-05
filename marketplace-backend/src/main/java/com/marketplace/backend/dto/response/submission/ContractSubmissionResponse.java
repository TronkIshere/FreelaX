package com.marketplace.backend.dto.response.submission;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ContractSubmissionResponse {
    private UUID id;
    private UUID contractId;
    private UUID milestoneId;
    private UUID freelancerId;
    private int version;
    private String status;
    private String summary;
    private Instant submittedAt;
    private boolean submittedLate;
    private Instant reviewDueAt;
    private Instant reviewGraceDueAt;
    private boolean reviewedAutomatically;
    private UUID disputeId;
    private String reviewerFeedback;
    private List<UUID> reviewCriterionIds;
    private List<UUID> reviewDeliverableIds;
    private List<Evidence> deliverables;
    private List<Evidence> acceptanceEvidence;

    public record Evidence(UUID requirementId, String description, String url) { }
}
