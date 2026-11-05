package com.marketplace.backend.dto.request.avalanche;

import java.util.UUID;

public record AvalancheActionInput(String idempotencyKey, UUID submissionId, UUID disputeId, Long newDeliveryDueAt, String by,
                    String clientSignature, String freelancerSignature, Long authorizationDeadline, Boolean releaseToFreelancer) {
    public AvalancheActionInput(String idempotencyKey, UUID submissionId, UUID disputeId, Long newDeliveryDueAt, String by,
                 String clientSignature, String freelancerSignature) {
        this(idempotencyKey, submissionId, disputeId, newDeliveryDueAt, by, clientSignature, freelancerSignature, null, null);
    }

    public AvalancheActionInput(String idempotencyKey, UUID submissionId, UUID disputeId, Long newDeliveryDueAt, String by,
                 String clientSignature, String freelancerSignature, Long authorizationDeadline) {
        this(idempotencyKey, submissionId, disputeId, newDeliveryDueAt, by, clientSignature, freelancerSignature, authorizationDeadline, null);
    }

    public static AvalancheActionInput empty() {
        return new AvalancheActionInput(null, null, null, null, null, null, null, null, null);
    }
}
