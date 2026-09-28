package com.marketplace.backend.dto.response.solana;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SolanaWithdrawalResult {
    String address;
    String withdrawalId;
    String freelancer;
    String tokenAmount;
    String mint;
    String treasury;
    String rateSnapshot;
    String fiatAmountVnd;
    String status;
    String requestedAt;
    String completedAt;
    String failureHash;
    String failedAt;
    String resolutionHash;
    String resolvedAt;
    String resolvedBy;
}
