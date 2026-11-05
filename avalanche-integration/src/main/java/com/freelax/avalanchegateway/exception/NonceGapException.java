package com.freelax.avalanchegateway.exception;

import com.freelax.avalanchegateway.applicationException.ApiException;
import com.freelax.avalanchegateway.applicationException.AvalancheErrorDefinitions;

/** Allocation for a signer is refused while one of its nonces is flagged as a gap: a later nonce would never execute. */
public class NonceGapException extends ApiException {

    public NonceGapException(String signerAddress) {
        super(AvalancheErrorDefinitions.NONCE_GAP, "signer " + signerAddress
                + " has a rejected transaction that left an unfilled nonce; operator action is required");
    }
}
