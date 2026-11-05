package com.freelax.avalanchegateway.dto.response;

public record HashStatusResponse(String environment, boolean isSimulated, long chainId, String txHash, String state, Long blockNumber) {

}
