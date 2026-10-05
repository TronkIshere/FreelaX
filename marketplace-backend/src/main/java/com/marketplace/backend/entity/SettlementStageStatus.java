package com.marketplace.backend.entity;

public enum SettlementStageStatus {
    NOT_STARTED, PROCESSING, SUCCEEDED, FAILED_RETRYABLE, FAILED, UNKNOWN;

    public boolean canAdvance() {
        return this != SUCCEEDED && this != FAILED;
    }
}
