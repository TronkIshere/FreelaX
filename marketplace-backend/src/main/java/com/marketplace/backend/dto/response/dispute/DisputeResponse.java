package com.marketplace.backend.dto.response.dispute;

import com.marketplace.backend.entity.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DisputeResponse(UUID disputeId, UUID contractId, UUID milestoneId, UUID jobId,
                              UUID submissionId, UUID openedBy, String reasonCode, String description,
                              DisputeStatus status, Instant openedAt, UUID claimedBy, Instant claimedAt,
                              Instant negotiationUntil, Instant moderationDueAt,
                              UUID negotiationProposedBy, String negotiationOutcome, String negotiationReason,
                              UUID resolvedBy, Instant decisionAt, Instant resolvedAt, String resolutionReason,
                              SettlementMoneyStatus refundStatus, String refundReference,
                              List<Evidence> evidence) {
    public record Evidence(UUID id, UUID actorId, DisputeEvidence.Kind kind, String text,
                           String url, String sha256, LocalDateTime createdAt) {}
    public static DisputeResponse from(ContractDispute d, List<DisputeEvidence> rows) {
        return new DisputeResponse(d.getId(), d.getContractId(), d.getMilestoneId(), d.getJobId(),
                d.getSubmissionId(), d.getOpenedBy(), d.getReasonCode(), d.getDescription(),
                d.getStatus(), d.getOpenedAt(), d.getClaimedBy(), d.getClaimedAt(),
                d.getNegotiationUntil(), d.getModerationDueAt(),
                d.getNegotiationProposedBy(), d.getNegotiationOutcome(), d.getNegotiationReason(),
                d.getResolvedBy(), d.getDecisionAt(), d.getResolvedAt(), d.getResolutionReason(),
                d.getRefundStatus(), d.getRefundReference(),
                rows.stream().map(e -> new Evidence(e.getId(), e.getActorId(), e.getKind(), e.getText(),
                        e.getUrl(), e.getSha256(), e.getCreatedAt())).toList());
    }
}
