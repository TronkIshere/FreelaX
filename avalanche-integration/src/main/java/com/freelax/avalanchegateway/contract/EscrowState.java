package com.freelax.avalanchegateway.contract;

import java.math.BigInteger;

/** On-chain escrow record as returned by getEscrow (contract state wins for custody, status, deadlines and counters). */
public record EscrowState(String client, String freelancer, String arbiter, BigInteger amount, int status, int maxRevisions,
                          int revisionsUsed, boolean extensionUsed, long reviewWindowSeconds, long submissionCount, long authNonce,
                          long deliveryDueAt, long originalDeliveryDueAt, long requestedDeliveryDueAt, long reviewDueAt,
                          String submissionHash, String resolutionHash, String disputedBy, long disputedAt) {

    public static final String[] STATUS_NAMES = {"None", "Funded", "Submitted", "Revision", "Disputed", "Released", "Refunded"};

    public String statusName() {
        return status >= 0 && status < STATUS_NAMES.length ? STATUS_NAMES[status] : "Unknown(" + status + ")";
    }
}
