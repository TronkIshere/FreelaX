package com.marketplace.backend.dto.response.job;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class JobSubmissionResponse {
    UUID id;
    UUID jobId;
    UUID freelancerId;
    int version;
    String summary;
    String deliverableUrl;
    String status;
    String reviewerFeedback;
    LocalDateTime reviewedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
