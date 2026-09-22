package com.marketplace.backend.dto.response.job;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JobResponse {
    UUID id;
    String title;
    String description;
    BigDecimal budgetUsd;
    UUID clientUserId;
    UUID freelancerUserId;
    String status;
    UUID checkoutOrderId;
    UUID payoutReleaseId;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
