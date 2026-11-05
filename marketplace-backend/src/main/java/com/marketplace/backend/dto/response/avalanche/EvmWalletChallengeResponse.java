package com.marketplace.backend.dto.response.avalanche;

import java.time.Instant;
import java.util.UUID;

public record EvmWalletChallengeResponse(UUID challengeId, String walletAddress, String message, Instant expiresAt) { }
