package com.marketplace.backend.blockchain;

public enum NeutralEscrowStatus {
    NONE, FUNDED, SUBMITTED, REVISION, DISPUTED, RELEASED, REFUNDED;

    public static NeutralEscrowStatus parse(String value) {
        if (value == null) {
            return NONE;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown escrow status " + value);
        }
    }

    public boolean isSettled() {
        return this == RELEASED || this == REFUNDED;
    }
}
