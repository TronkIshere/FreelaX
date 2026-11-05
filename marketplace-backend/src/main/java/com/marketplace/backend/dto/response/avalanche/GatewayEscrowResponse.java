package com.marketplace.backend.dto.response.avalanche;

public record GatewayEscrowResponse(String environment, boolean isSimulated, long chainId, String escrowId, String status, String client, String freelancer,
                     String arbiter, String amountBaseUnits, int maxRevisions, int revisionsUsed, long submissionCount, long deliveryDueAt,
                     long reviewDueAt, String submissionHash, String resolutionHash) {
}
