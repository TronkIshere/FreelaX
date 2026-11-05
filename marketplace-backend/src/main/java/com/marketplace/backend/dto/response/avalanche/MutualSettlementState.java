package com.marketplace.backend.dto.response.avalanche;

/** R12: a mutual settlement proposal (one per outcome) signed by the parties' own wallets over one deadline and digest. */
public record MutualSettlementState(boolean releaseToFreelancer, long authorizationDeadline, String typedDataDigest,
                                    boolean clientSigned, boolean freelancerSigned) {
}
