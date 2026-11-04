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
    /** Chain network of the escrow (V6 {@code payment_network_id}); null for every legacy Solana rail. Additive (A7). */
    String paymentNetworkId;
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
