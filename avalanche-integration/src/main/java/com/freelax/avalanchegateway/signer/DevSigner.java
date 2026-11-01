package com.freelax.avalanchegateway.signer;

import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.env.EnvironmentGuard;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

/**
 * Ephemeral in-memory development signer. Package-private: it can only be built through {@link DevSignerFactory}.
 * The key lives only in this object's memory; it is never written, logged or exposed (see {@link #toString()}).
 */
final class DevSigner implements Signer {

    private static final BigInteger HALF_CURVE_ORDER = CustomNamedCurves.getByName("secp256k1").getN().shiftRight(1);

    private final SignerRole role;
    private final AvalancheEnvironment environment;
    private final long chainId;
    private final ECKeyPair keyPair;
    private final String address;

    DevSigner(SignerRole role, AvalancheEnvironment environment, long chainId, ECKeyPair keyPair) {
        EnvironmentGuard.requireSignerEnvironment(environment);
        EnvironmentGuard.checkChainId(environment, chainId);
        this.role = role;
        this.environment = environment;
        this.chainId = chainId;
        this.keyPair = keyPair;
        this.address = Keys.toChecksumAddress(Keys.getAddress(keyPair.getPublicKey()));
    }

    @Override
    public SignerRole role() {
        return role;
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public AvalancheEnvironment environment() {
        return environment;
    }

    @Override
    public long chainId() {
        return chainId;
    }

    @Override
    public Signature sign(byte[] digest32) {
        EnvironmentGuard.requireSignerEnvironment(environment);
        EnvironmentGuard.checkChainId(environment, chainId);
        if (digest32 == null || digest32.length != 32) {
            throw new IllegalArgumentException("a signer signs exactly one 32-byte digest");
        }
        Sign.SignatureData data = Sign.signMessage(digest32, keyPair, false);
        BigInteger s = Numeric.toBigInt(data.getS());
        if (s.compareTo(HALF_CURVE_ORDER) > 0) {
            throw new IllegalStateException("produced a high-s signature, which contracts reject");
        }
        int v = data.getV()[0] & 0xff;
        return new Signature(data.getR(), data.getS(), v);
    }

    @Override
    public String toString() {
        return "DevSigner[role=" + role + ", address=" + address + ", environment=" + environment + "]";
    }
}
