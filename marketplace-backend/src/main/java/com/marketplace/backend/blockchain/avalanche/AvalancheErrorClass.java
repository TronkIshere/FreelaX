package com.marketplace.backend.blockchain.avalanche;

/** Error classes the marketplace itself records on a chain transaction row; the names are persisted and never change. */
public enum AvalancheErrorClass {
    /** The chain state read after finalization does not agree with the recorded terms. */
    CHAIN_MISMATCH,
    /** The gateway refused the call definitively. */
    GATEWAY_REJECTED,
    /** The gateway could not be reached or answered ambiguously; the outcome is unknown. */
    GATEWAY_UNREACHABLE
}
