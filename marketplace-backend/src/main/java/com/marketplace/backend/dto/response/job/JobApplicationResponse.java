package com.marketplace.backend.dto.response.job;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JobApplicationResponse {
    UUID id;
    UUID jobId;
    UUID freelancerId;
    String status;
    String acceptedTermsFingerprint;
    java.time.Instant termsAcceptedAt;
    LocalDateTime createdAt;
}
