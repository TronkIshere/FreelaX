package com.marketplace.backend.dto.request.cancellation;
import jakarta.validation.constraints.NotNull;
public record CancellationDecisionRequest(@NotNull Decision decision) {
    public enum Decision { ACCEPT, REJECT }
}
