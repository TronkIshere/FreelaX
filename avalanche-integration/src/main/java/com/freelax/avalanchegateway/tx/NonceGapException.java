package com.freelax.avalanchegateway.tx;

/** Allocation for a signer is refused while one of its nonces is flagged as a gap: a later nonce would never execute. */
public class NonceGapException extends IllegalStateException {

    public NonceGapException(String signerAddress) {
        super("NONCE_GAP: signer " + signerAddress + " has a rejected transaction that left an unfilled nonce; operator action is required");
    }
}
