package com.marketplace.backend.dto.response.funding;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class FundingResponse {
    private UUID fundingTransactionId;
    private String fundingStatus;
    private boolean simulation;
    private UUID providerReference;
    private String nextAction;
    private Integer retryAfterSeconds;
}
