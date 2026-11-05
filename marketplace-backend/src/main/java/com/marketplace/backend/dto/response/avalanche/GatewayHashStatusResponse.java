package com.marketplace.backend.dto.response.avalanche;

public record GatewayHashStatusResponse(String environment, boolean isSimulated, long chainId, String txHash, String state, Long blockNumber) {
}
