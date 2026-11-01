package com.freelax.avalanchegateway.signer;

/** One distinct ephemeral key per role. The test roles are the backend development wallets of LOCAL_SIMULATION. */
public enum SignerRole {
    AUTHORIZATION,
    ARBITER,
    RELAYER,
    PAUSER,
    ADMIN,
    DEPLOYER,
    TEST_BUYER,
    TEST_FREELANCER
}
