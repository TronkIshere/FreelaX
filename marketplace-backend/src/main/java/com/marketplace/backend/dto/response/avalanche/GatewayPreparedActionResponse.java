package com.marketplace.backend.dto.response.avalanche;

import java.util.List;

public record GatewayPreparedActionResponse(String environment, boolean isSimulated, long chainId, String action, String escrowId, List<GatewayPreparedTxResponse> transactions,
                       Long authorizationDeadline, String typedData, String typedDataDigest) {
}
