package com.marketplace.backend.dto.response.avalanche;

public record GatewayTransactionResponse(String environment, boolean isSimulated, long chainId, String operationKey, String state, String txHash,
                          Long nonce, Long blockNumber, String errorClass, String signerRole) {
}
