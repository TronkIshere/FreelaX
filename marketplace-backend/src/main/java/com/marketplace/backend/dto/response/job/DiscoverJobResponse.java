package com.marketplace.backend.dto.response.job;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class DiscoverJobResponse {
    UUID id;
    String title;
    String description;
    com.marketplace.backend.entity.JobCategory category;
    List<String> skills;
    BigDecimal budgetUsd;
    String status;
    JobClientSummaryResponse client;
    boolean hasApplied;
    UUID applicationId;
    String applicationStatus;
    Instant deliveryDueAt;
    int reviewWindowHours;
    int maxRevisions;
    List<RequirementResponse> deliverables;
    List<RequirementResponse> acceptanceCriteria;
    LocalDateTime createdAt;
}
