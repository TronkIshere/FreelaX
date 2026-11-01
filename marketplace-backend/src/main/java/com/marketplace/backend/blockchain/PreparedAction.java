package com.marketplace.backend.blockchain;

import java.util.List;

public record PreparedAction(String action, String escrowId, EnvironmentInfo environment, List<PreparedTransaction> transactions,
                             Long authorizationDeadline, String typedData, String typedDataDigest) {
}
