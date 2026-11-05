package com.freelax.avalanchegateway.signer;

import com.freelax.avalanchegateway.applicationException.EnvironmentErrorDefinitions;
import com.freelax.avalanchegateway.applicationException.EnvironmentGuardException;
import com.freelax.avalanchegateway.env.EnvironmentGuard;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import org.web3j.crypto.Keys;

import java.security.GeneralSecurityException;
import java.util.EnumMap;

/**
 * The only way to obtain a development signer. It needs a validated environment, refuses everything except
 * LOCAL_SIMULATION on a local Anvil-family node, and generates a fresh random key per signer. Keys are never persisted.
 */
public final class DevSignerFactory {

    private DevSignerFactory() {
    }

    public static Signer create(ValidatedEnvironment validated, SignerRole role) {
        check(validated);
        try {
            return new DevSigner(role, validated.environment(), validated.chainId(), Keys.createEcKeyPair());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("could not generate an ephemeral key", e);
        }
    }

    public static SignerRegistry createRegistry(ValidatedEnvironment validated) {
        check(validated);
        EnumMap<SignerRole, Signer> signers = new EnumMap<>(SignerRole.class);
        for (SignerRole role : SignerRole.values()) {
            signers.put(role, create(validated, role));
        }
        return new SignerRegistry(signers);
    }

    private static void check(ValidatedEnvironment validated) {
        if (validated == null) {
            throw new EnvironmentGuardException(EnvironmentErrorDefinitions.ENVIRONMENT_MISSING, "a signer needs a validated environment");
        }
        EnvironmentGuard.requireSignerEnvironment(validated.environment());
        EnvironmentGuard.checkChainId(validated.environment(), validated.chainId());
        if (!"anvil".equals(validated.clientFamily())) {
            throw new EnvironmentGuardException(EnvironmentErrorDefinitions.CLIENT_FAMILY_MISMATCH, "development signers require a local anvil node");
        }
    }
}
