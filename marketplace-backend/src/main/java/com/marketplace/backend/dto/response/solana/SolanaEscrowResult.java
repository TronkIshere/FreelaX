package com.marketplace.backend.dto.response.solana;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SolanaEscrowResult(
        String address, String milestoneId, String client, String freelancer, String arbiter,
        String mint, String amount, String fundingExpiresAt,
        String originalDeliveryDueAt, String deliveryDueAt,
        String requestedDeliveryDueAt, boolean extensionUsed, String reviewWindowSeconds,
        String reviewDueAt, String submissionHash, int submissionCount, int revisionsUsed,
        int maxRevisions, String status, String fundedAt, String settledAt,
        String disputeHash, String disputedBy, String disputedAt,
        String resolutionHash, String revisionHash, int bump,
        String vaultAddress, String vaultBalanceBaseUnits
) { }
