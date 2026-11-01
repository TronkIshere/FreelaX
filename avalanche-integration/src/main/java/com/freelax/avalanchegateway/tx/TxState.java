package com.freelax.avalanchegateway.tx;

/**
 * Owner-approved state set (O-17). Submission and pending are never success: only CONFIRMED (receipt status 1) and
 * FINALIZED (finality policy met) carry on-chain success.
 * <ul>
 * <li>CREATED: operation recorded and nonce allocated; nothing signed yet.</li>
 * <li>SIGNED: raw transaction and hash persisted, not yet accepted by a node.</li>
 * <li>SUBMITTED: a node returned the hash (acceptance, not success).</li>
 * <li>PENDING: a node knows the transaction but there is no receipt.</li>
 * <li>CONFIRMED: receipt verified (status 1, expected target); finality policy not yet met.</li>
 * <li>FINALIZED: configured finality condition satisfied.</li>
 * <li>REVERTED: mined, execution failed (receipt status 0).</li>
 * <li>FAILED: definitive application-level failure without successful on-chain execution.</li>
 * <li>REPLACED: another transaction consumed the nonce.</li>
 * <li>UNKNOWN: the outcome cannot yet be established; reconciliation required.</li>
 * </ul>
 */
public enum TxState {
    CREATED, SIGNED, SUBMITTED, PENDING, CONFIRMED, FINALIZED, REVERTED, FAILED, REPLACED, UNKNOWN;

    public boolean terminal() {
        return this == FINALIZED || this == REVERTED || this == FAILED || this == REPLACED;
    }
}
