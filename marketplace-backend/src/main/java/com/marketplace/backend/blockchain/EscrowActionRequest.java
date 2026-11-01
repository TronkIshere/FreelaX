package com.marketplace.backend.blockchain;

import java.util.Map;

public record EscrowActionRequest(String escrowId, String action, String operationKey, Map<String, Object> parameters) {
}
