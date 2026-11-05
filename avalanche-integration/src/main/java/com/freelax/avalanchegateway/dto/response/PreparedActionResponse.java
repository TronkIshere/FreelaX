package com.freelax.avalanchegateway.dto.response;

import java.util.List;

public record PreparedActionResponse(String environment, boolean isSimulated, long chainId, String action, String escrowId,
                                     List<PreparedTx> transactions, Long authorizationDeadline, String typedData, String typedDataDigest) {

}
