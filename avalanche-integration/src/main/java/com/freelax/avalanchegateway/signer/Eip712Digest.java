package com.freelax.avalanchegateway.signer;

import org.web3j.crypto.StructuredDataEncoder;

/**
 * EIP-712 digest of a typed-data JSON document (domain separator + struct hash). Chain-specific escrow structs are
 * defined with the contracts in a later milestone; this helper is generic and signs nothing.
 */
public final class Eip712Digest {

    private Eip712Digest() {
    }

    public static byte[] hash(String typedDataJson) {
        try {
            return new StructuredDataEncoder(typedDataJson).hashStructuredData();
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid EIP-712 typed data", e);
        }
    }
}
