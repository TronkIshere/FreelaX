package com.marketplace.backend.blockchain;

public enum TransactionStatus {
    CREATED, SIGNED, SUBMITTED, PENDING, CONFIRMED, FINALIZED, REVERTED, FAILED, REPLACED, UNKNOWN;

    public boolean isSuccessfulSettlement() {
        return this == FINALIZED;
    }

    public boolean isTerminal() {
        return this == FINALIZED || this == REVERTED || this == FAILED || this == REPLACED;
    }

    public boolean isDefinitiveFailure() {
        return this == REVERTED || this == FAILED || this == REPLACED;
    }
}
