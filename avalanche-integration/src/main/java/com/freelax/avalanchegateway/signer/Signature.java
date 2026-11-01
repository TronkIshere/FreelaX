package com.freelax.avalanchegateway.signer;

import org.web3j.utils.Numeric;

import java.util.Arrays;

/** A secp256k1 signature over a 32-byte digest: r and s as 32-byte big-endian values, v as 27 or 28; s is always low. */
public record Signature(byte[] r, byte[] s, int v) {

    public Signature {
        r = r.clone();
        s = s.clone();
    }

    @Override
    public byte[] r() {
        return r.clone();
    }

    @Override
    public byte[] s() {
        return s.clone();
    }

    /** The 65-byte r || s || v encoding used by ecrecover and OpenZeppelin ECDSA. */
    public String toHex() {
        byte[] all = new byte[65];
        System.arraycopy(r, 0, all, 0, 32);
        System.arraycopy(s, 0, all, 32, 32);
        all[64] = (byte) v;
        return Numeric.toHexString(all);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Signature sig && Arrays.equals(r, sig.r) && Arrays.equals(s, sig.s) && v == sig.v;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Arrays.hashCode(r) + Arrays.hashCode(s)) + v;
    }
}
