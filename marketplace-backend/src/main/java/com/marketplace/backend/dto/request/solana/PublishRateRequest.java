package com.marketplace.backend.dto.request.solana;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PublishRateRequest {
    String rateAuthority;
    String rateId;
    String usdcUsdE6;
    String usdVndE6;
    String observedAt;
    String expiresAt;
    String sourceHash;
    String mode;
    String commitment;
    Boolean skipPreflight;
}
