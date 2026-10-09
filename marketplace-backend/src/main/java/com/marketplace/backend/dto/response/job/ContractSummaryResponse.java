package com.marketplace.backend.dto.response.job;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ContractSummaryResponse {
    UUID id;
    String status;
    String paymentRail;
    UUID milestoneId;
    String milestoneStatus;
    BigDecimal amount;
    String currency;
    Instant deliveryDueAt;
    int reviewWindowHours;
    int maxRevisions;
    int revisionsUsed;
    List<RequirementResponse> deliverables;
    List<RequirementResponse> acceptanceCriteria;
}
