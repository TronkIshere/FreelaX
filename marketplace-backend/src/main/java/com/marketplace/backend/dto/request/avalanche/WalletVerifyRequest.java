package com.marketplace.backend.dto.request.avalanche;

import java.util.UUID;

public record WalletVerifyRequest(UUID challengeId, String signature) { }
