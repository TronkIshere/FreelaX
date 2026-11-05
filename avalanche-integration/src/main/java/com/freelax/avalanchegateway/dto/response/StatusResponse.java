package com.freelax.avalanchegateway.dto.response;

public record StatusResponse(String environment, boolean isSimulated, long validatedChainId, long liveChainId,
                             String clientVersion, String endpointHost, String javaVersion) {

}
