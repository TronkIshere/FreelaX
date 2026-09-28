package com.marketplace.backend.dto.response.solana;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SolanaRateResult {
    String address;
    String rateId;
    String usdcUsdE6;
    String usdVndE6;
    String usdcVndE6;
    String observedAt;
    String expiresAt;
    String sourceHash;
    String publisher;
}
