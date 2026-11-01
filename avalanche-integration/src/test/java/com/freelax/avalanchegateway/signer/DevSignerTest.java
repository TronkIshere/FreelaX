package com.freelax.avalanchegateway.signer;

import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.env.EnvironmentGuardException;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevSignerTest {

    private static final BigInteger HALF_ORDER = CustomNamedCurves.getByName("secp256k1").getN().shiftRight(1);

    private static Signer localSigner(SignerRole role) {
        return DevSignerFactory.create(new ValidatedEnvironment(AvalancheEnvironment.LOCAL_SIMULATION, 31337, "anvil", "127.0.0.1"), role);
    }

    private static BigInteger recover(byte[] digest, Signature signature) throws java.security.SignatureException {
        return Sign.signedMessageHashToKey(digest, new Sign.SignatureData((byte) signature.v(), signature.r(), signature.s()));
    }

    @Test
    void signaturesAreLowSAndRecoverToTheSignerAddress() throws Exception {
        SecureRandom random = new SecureRandom();
        for (SignerRole role : new SignerRole[]{SignerRole.AUTHORIZATION, SignerRole.ARBITER, SignerRole.RELAYER}) {
            Signer signer = localSigner(role);
            for (int i = 0; i < 150; i++) {
                byte[] digest = new byte[32];
                random.nextBytes(digest);
                Signature signature = signer.sign(digest);
                assertTrue(Numeric.toBigInt(signature.s()).compareTo(HALF_ORDER) <= 0, "low-s");
                assertTrue(signature.v() == 27 || signature.v() == 28);
                assertEquals(signer.address(), Keys.toChecksumAddress(Keys.getAddress(recover(digest, signature))));
            }
        }
    }

    @Test
    void signsOnlyExactly32ByteDigests() {
        Signer signer = localSigner(SignerRole.ADMIN);
        assertThrows(IllegalArgumentException.class, () -> signer.sign(null));
        assertThrows(IllegalArgumentException.class, () -> signer.sign(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> signer.sign(new byte[31]));
        assertThrows(IllegalArgumentException.class, () -> signer.sign(new byte[33]));
    }

    @Test
    void keysAreEphemeralAndDifferPerInstance() {
        assertNotEquals(localSigner(SignerRole.ADMIN).address(), localSigner(SignerRole.ADMIN).address());
    }

    @Test
    void keyMaterialIsNotReachableThroughThePublicSurface() throws Exception {
        Signer signer = localSigner(SignerRole.AUTHORIZATION);
        String privateKeyHex = privateKeyHex(signer);
        assertFalse(signer.toString().toLowerCase().contains(privateKeyHex));
        SignerRegistry registry = DevSignerFactory.createRegistry(new ValidatedEnvironment(AvalancheEnvironment.LOCAL_SIMULATION, 31337, "anvil", "127.0.0.1"));
        assertFalse(registry.addresses().toString().toLowerCase().contains(privateKeyHex));
        for (var method : Signer.class.getMethods()) {
            assertFalse(method.getName().toLowerCase().contains("private") || method.getName().toLowerCase().contains("key"),
                    "no accessor may hand out key material: " + method.getName());
        }
    }

    @Test
    void signingIsRefusedOnceTheBoundEnvironmentIsNotLocalSimulation() throws Exception {
        Signer signer = localSigner(SignerRole.AUTHORIZATION);
        Field environment = signer.getClass().getDeclaredField("environment");
        environment.setAccessible(true);
        environment.set(signer, AvalancheEnvironment.AVALANCHE_FUJI);
        EnvironmentGuardException e = assertThrows(EnvironmentGuardException.class, () -> signer.sign(new byte[32]));
        assertEquals("SIGNER_FORBIDDEN_ENVIRONMENT", e.code());
    }

    @Test
    void signingIsRefusedOnceTheBoundChainIdIsMainnet() throws Exception {
        Signer signer = localSigner(SignerRole.AUTHORIZATION);
        Field chain = signer.getClass().getDeclaredField("chainId");
        chain.setAccessible(true);
        chain.setLong(signer, 43114L);
        assertEquals("REJECTED_MAINNET_CHAIN_ID", assertThrows(EnvironmentGuardException.class, () -> signer.sign(new byte[32])).code());
    }

    @Test
    void constructorRefusesNonLocalEnvironments() {
        ECKeyPair key;
        try {
            key = Keys.createEcKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        assertEquals("SIGNER_FORBIDDEN_ENVIRONMENT", assertThrows(EnvironmentGuardException.class,
                () -> new DevSigner(SignerRole.ADMIN, AvalancheEnvironment.AVALANCHE_FUJI, 43113, key)).code());
    }

    /** Known answer from the EIP-712 specification example (spec vector; no network, no key in the repository). */
    @Test
    void eip712SpecVectorDigestAndSignatureMatch() {
        String typedData = """
                {"types":{"EIP712Domain":[{"name":"name","type":"string"},{"name":"version","type":"string"},{"name":"chainId","type":"uint256"},{"name":"verifyingContract","type":"address"}],
                "Person":[{"name":"name","type":"string"},{"name":"wallet","type":"address"}],
                "Mail":[{"name":"from","type":"Person"},{"name":"to","type":"Person"},{"name":"contents","type":"string"}]},
                "primaryType":"Mail",
                "domain":{"name":"Ether Mail","version":"1","chainId":1,"verifyingContract":"0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"},
                "message":{"from":{"name":"Cow","wallet":"0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"},
                "to":{"name":"Bob","wallet":"0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB"},"contents":"Hello, Bob!"}}
                """;
        byte[] digest = Eip712Digest.hash(typedData);
        assertEquals("0xbe609aee343fb3c4b28e1df9e632fca64fcfaede20f02e86244efddf30957bd2", Numeric.toHexString(digest));

        // The specification's published signature must recover to its published address under our digest.
        // (No key is embedded in this repository: recovery needs only the public signature.)
        Signature published = new Signature(
                Numeric.hexStringToByteArray("0x4355c47d63924e8a72e509b65029052eb6c299d53a04e167c5775fd466751c9d"),
                Numeric.hexStringToByteArray("0x07299936d304c153f6443dfa05f40ff007d72911b6f72307f996231605b91562"), 28);
        assertEquals("0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826",
                Keys.toChecksumAddress(Keys.getAddress(recoverUnchecked(digest, published))));
        assertEquals("0x4355c47d63924e8a72e509b65029052eb6c299d53a04e167c5775fd466751c9d"
                + "07299936d304c153f6443dfa05f40ff007d72911b6f72307f996231605b91562" + "1c", published.toHex());
        assertArrayEquals(Numeric.hexStringToByteArray("0x4355c47d63924e8a72e509b65029052eb6c299d53a04e167c5775fd466751c9d"), published.r());
    }

    private static BigInteger recoverUnchecked(byte[] digest, Signature signature) {
        try {
            return recover(digest, signature);
        } catch (java.security.SignatureException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void invalidTypedDataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Eip712Digest.hash("{not json"));
    }

    static String privateKeyHex(Signer signer) throws Exception {
        Field field = signer.getClass().getDeclaredField("keyPair");
        field.setAccessible(true);
        return Numeric.toHexStringNoPrefixZeroPadded(((ECKeyPair) field.get(signer)).getPrivateKey(), 64).toLowerCase();
    }
}
