package com.marketplace.backend.dto.response.avalanche;

/** F13: progress of a mutual refund signed by both parties' own wallets over the same deadline and digest. */
public record MutualRefundState(long authorizationDeadline, String typedDataDigest, boolean clientSigned, boolean freelancerSigned) {
}
