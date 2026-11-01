package com.freelax.avalanchegateway.signer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Holds one signer per role for the lifetime of the process. Exposes addresses only. */
public final class SignerRegistry {

    private final Map<SignerRole, Signer> signers;

    SignerRegistry(EnumMap<SignerRole, Signer> signers) {
        this.signers = Collections.unmodifiableMap(new EnumMap<>(signers));
    }

    public Signer get(SignerRole role) {
        Signer signer = signers.get(role);
        if (signer == null) {
            throw new IllegalArgumentException("no signer for role " + role);
        }
        return signer;
    }

    public Map<SignerRole, String> addresses() {
        EnumMap<SignerRole, String> out = new EnumMap<>(SignerRole.class);
        signers.forEach((role, signer) -> out.put(role, signer.address()));
        return Collections.unmodifiableMap(out);
    }
}
