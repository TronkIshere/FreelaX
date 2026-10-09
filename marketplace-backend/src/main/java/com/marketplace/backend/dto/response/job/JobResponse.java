package com.marketplace.backend.dto.response.job;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JobResponse {
    UUID id;
    String title;
    String description;
    com.marketplace.backend.entity.JobCategory category;
    List<String> skills;
    BigDecimal budgetUsd;
    UnifiedTermsPreviewResponse localPaymentTerms;
    UUID clientUserId;
    UUID freelancerId;
    String status;
    UUID checkoutOrderId;
    String taxExportStatus;
    Instant deliveryDueAt;
    int reviewWindowHours;
    int maxRevisions;
    List<RequirementResponse> deliverables;
    List<RequirementResponse> acceptanceCriteria;
    ContractSummaryResponse contract;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
