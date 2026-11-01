package com.marketplace.backend.blockchain;

public record EscrowSnapshot(EscrowReference reference, AssetReference asset, NeutralEscrowStatus status, String client, String freelancer,
                             String arbiter, String amountBaseUnits, int maxRevisions, int revisionsUsed, long submissionCount,
                             long deliveryDueAt, long reviewDueAt, String submissionHash, String resolutionHash) {
}
