package com.freelax.avalanchegateway.dto.response;

/** Responses always say which environment they come from and that the money is simulated. */
public record ConfigResponse(String environment, boolean isSimulated, long chainId, String escrow, String token, String tokenKind,
                             String contractVersion, boolean paused, String defaultArbiter, boolean backendSignersAvailable,
                             String explorerBaseUrl) {

}
