package com.marketplace.backend.dto.request.dispute;

public record ResolveDisputeRequest(Outcome outcome, String reason) {
    public enum Outcome { RELEASE_TO_FREELANCER, REFUND_TO_CLIENT }
}
