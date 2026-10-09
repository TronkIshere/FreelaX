package com.marketplace.backend.dto.response.solana;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SolanaBuildResult(
        String status, String instruction, String buildSessionId, String transactionBase64,
        String feePayer, String recentBlockhash, long lastValidBlockHeight,
        List<String> requiredSigners, DerivedAccountsResult derivedAccounts
) { }
