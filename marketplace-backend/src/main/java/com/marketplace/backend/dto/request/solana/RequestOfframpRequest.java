package com.marketplace.backend.dto.request.solana;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RequestOfframpRequest {
    String freelancer;
    String withdrawalId;
    String rateId;
    String tokenAmount;
    String mode;
    String commitment;
    Boolean skipPreflight;
}
