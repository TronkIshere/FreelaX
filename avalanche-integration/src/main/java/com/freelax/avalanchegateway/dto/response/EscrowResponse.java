package com.freelax.avalanchegateway.dto.response;

public record EscrowResponse(String environment, boolean isSimulated, long chainId, String escrowId, String status, String client,
                             String freelancer, String arbiter, String amountBaseUnits, int maxRevisions, int revisionsUsed,
                             boolean extensionUsed, long reviewWindowSeconds, long submissionCount, long authNonce, long deliveryDueAt,
                             long originalDeliveryDueAt, long requestedDeliveryDueAt, long reviewDueAt, String submissionHash,
                             String resolutionHash, String disputedBy, long disputedAt) {

}
