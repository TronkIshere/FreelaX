package com.marketplace.backend.blockchain.avalanche;

import java.util.Arrays;
import java.util.Optional;

/** The escrow actions of the Avalanche rail. {@link #value()} is the wire and persisted spelling and never changes. */
public enum AvalancheAction {
    FUND("fund"),
    SUBMIT("submit"),
    REVISE("revise"),
    EXTENSION_REQUEST("extension-request"),
    EXTENSION_APPROVE("extension-approve"),
    DISPUTE("dispute"),
    RELEASE("release"),
    RESOLVE("resolve"),
    MUTUAL_REFUND("mutual-refund"),
    /** R12 (owner decision 2026-11-05): both parties settle a Disputed escrow. */
    MUTUAL_SETTLE("mutual-settle");

    private final String value;

    AvalancheAction(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<AvalancheAction> parse(String value) {
        return Arrays.stream(values()).filter(action -> action.value.equals(value)).findFirst();
    }

    /**
     * Key under which the first signature of a two-party authorization is held. Pending settlement pairs are keyed per proposed
     * outcome; a mutual refund has a single pair.
     */
    public String pendingKey(boolean releaseToFreelancer) {
        return this == MUTUAL_SETTLE ? value + (releaseToFreelancer ? ":release" : ":refund") : value;
    }
}
