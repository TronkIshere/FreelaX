package com.marketplace.backend.dto.response.avalanche;

/** {@code payout} is null for UNSUPPORTED_RAIL; {@code reason} says why (stable code). */
public record AvalanchePayoutResult(AvalanchePayoutOutcome outcome, String reason, AvalanchePayoutView payout) {

    public static AvalanchePayoutResult unsupported(String reason) {
        return new AvalanchePayoutResult(AvalanchePayoutOutcome.UNSUPPORTED_RAIL, reason, null);
    }
}
