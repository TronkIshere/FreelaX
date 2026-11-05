package com.marketplace.backend.dto.request.avalanche;

public record RecordTransactionRequest(String txHash, AvalancheActionInput input) {
}
