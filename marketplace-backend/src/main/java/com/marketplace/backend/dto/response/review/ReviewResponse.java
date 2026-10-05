package com.marketplace.backend.dto.response.review;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.marketplace.backend.entity.ContractReview;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewResponse(UUID id, UUID contractId, UUID reviewerId, UUID revieweeId,
                             boolean submitted, Instant submittedAt, Instant publishedAt,
                             Integer overall, Dimensions dimensions, String comment,
                             boolean contentHidden, boolean reported) {
    public record Dimensions(Integer communication, Integer requirementsOrQuality, Integer timeliness) {}

    public static ReviewResponse from(ContractReview row, boolean visible, boolean showHiddenComment) {
        boolean submitted = row.getSubmittedAt() != null;
        return new ReviewResponse(row.getId(), row.getContractId(), row.getReviewerId(), row.getRevieweeId(),
                visible && submitted, visible ? row.getSubmittedAt() : null, row.getPublishedAt(),
                visible ? row.getOverall() : null,
                visible && submitted ? new Dimensions(row.getCommunication(), row.getRequirementsOrQuality(), row.getTimeliness()) : null,
                visible && (row.getHiddenAt() == null || showHiddenComment) ? row.getComment() : null,
                row.getHiddenAt() != null, row.getReportedAt() != null);
    }
}
