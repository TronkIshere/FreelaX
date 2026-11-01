package com.freelax.avalanchegateway.signer;

import com.freelax.avalanchegateway.env.AvalancheEnvironment;

/**
 * Signs 32-byte digests and nothing else. It cannot build, send or authorize anything: transaction submission and the
 * business decision of what to sign belong to other components. There is deliberately no accessor for key material.
 */
public interface Signer {

    SignerRole role();

    /** EIP-55 checksummed address of this signer. */
    String address();

    AvalancheEnvironment environment();

    /** The chain id that was verified when this signer was created; signing helpers must use this value only. */
    long chainId();

    Signature sign(byte[] digest32);
}
