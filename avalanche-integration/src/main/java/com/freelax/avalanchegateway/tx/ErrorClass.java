package com.freelax.avalanchegateway.tx;

/** Why the last attempt did not progress. Only PERMANENT_REJECTION is definitive; everything else is retried or reconciled. */
public enum ErrorClass {
    ALREADY_KNOWN,
    NONCE_TOO_LOW,
    UNDERPRICED,
    INSUFFICIENT_FUNDS,
    PERMANENT_REJECTION,
    RATE_LIMITED,
    TRANSPORT,
    UNCLASSIFIED_RPC_ERROR,
    RECEIPT_TARGET_MISMATCH,
    HASH_MISMATCH,
    SIGNER_UNAVAILABLE
}
