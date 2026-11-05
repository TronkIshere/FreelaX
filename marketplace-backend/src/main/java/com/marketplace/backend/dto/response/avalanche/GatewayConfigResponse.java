package com.marketplace.backend.dto.response.avalanche;

public record GatewayConfigResponse(String environment, boolean isSimulated, long chainId, String escrow, String token, String tokenKind,
                     String contractVersion, boolean paused, String defaultArbiter, boolean backendSignersAvailable,
                     String explorerBaseUrl) {
}
