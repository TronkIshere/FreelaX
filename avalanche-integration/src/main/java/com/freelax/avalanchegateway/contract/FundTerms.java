package com.freelax.avalanchegateway.contract;

import java.math.BigInteger;

/** The terms of an escrow as authorized by the backend and funded by the client (mirrors IFreelaXEscrow.FundTerms). */
public record FundTerms(String escrowId, String client, String freelancer, String token, BigInteger amount,
                        long fundingExpiresAt, long deliveryDueAt, int reviewWindowHours, int maxRevisions,
                        boolean highValueGrace, String arbiter) {
}
