package com.marketplace.backend.dto.request.avalanche;

import java.util.UUID;

public record MutualSettlementSignatureRequest(UUID disputeId, Boolean releaseToFreelancer, Long authorizationDeadline,
                                               String typedDataDigest, String signature) {
}
