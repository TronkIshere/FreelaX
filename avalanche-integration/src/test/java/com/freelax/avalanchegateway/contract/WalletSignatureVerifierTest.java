package com.freelax.avalanchegateway.contract;

import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Register item A8: EIP-191 personal_sign verification for wallet linking. Keys here are throwaway test keys. */
class WalletSignatureVerifierTest {

    private static final String MESSAGE = "FreelaX wallet binding\nUser: 00000000-0000-0000-0000-000000000001\nNonce: abc";

    private static String sign(ECKeyPair key, String message) {
        Sign.SignatureData d = Sign.signPrefixedMessage(message.getBytes(StandardCharsets.UTF_8), key);
        return Numeric.toHexString(d.getR()) + Numeric.toHexStringNoPrefix(d.getS()) + Numeric.toHexStringNoPrefix(d.getV());
    }

    private static String address(ECKeyPair key) {
        return Keys.toChecksumAddress("0x" + Keys.getAddress(key));
    }

    @Test
    void acceptsTheSignerOfTheExactMessageInAnyAddressCase() throws Exception {
        ECKeyPair key = Keys.createEcKeyPair();
        String sig = sign(key, MESSAGE);
        assertTrue(WalletSignatureVerifier.verifyPersonalSign(address(key), MESSAGE, sig));
        assertTrue(WalletSignatureVerifier.verifyPersonalSign(address(key).toLowerCase(), MESSAGE, sig));
    }

    @Test
    void rejectsAnotherMessageOrAnotherAddress() throws Exception {
        ECKeyPair key = Keys.createEcKeyPair();
        ECKeyPair other = Keys.createEcKeyPair();
        String sig = sign(key, MESSAGE);
        assertFalse(WalletSignatureVerifier.verifyPersonalSign(address(key), MESSAGE + " ", sig));
        assertFalse(WalletSignatureVerifier.verifyPersonalSign(address(other), MESSAGE, sig));
    }

    @Test
    void acceptsVAsZeroOrOneButRejectsOtherRecoveryIds() throws Exception {
        ECKeyPair key = Keys.createEcKeyPair();
        String sig = sign(key, MESSAGE);
        int v = Integer.parseInt(sig.substring(130), 16);
        String low = sig.substring(0, 130) + String.format("%02x", v - 27);
        assertTrue(WalletSignatureVerifier.verifyPersonalSign(address(key), MESSAGE, low));
        assertFalse(WalletSignatureVerifier.verifyPersonalSign(address(key), MESSAGE, sig.substring(0, 130) + "1d"));
    }

    @Test
    void rejectsTheMalleableHighSCopy() throws Exception {
        ECKeyPair key = Keys.createEcKeyPair();
        String sig = sign(key, MESSAGE);
        BigInteger s = new BigInteger(sig.substring(66, 130), 16);
        BigInteger highS = Sign.CURVE_PARAMS.getN().subtract(s);
        int v = Integer.parseInt(sig.substring(130), 16);
        String flipped = sig.substring(0, 66) + String.format("%064x", highS) + String.format("%02x", v == 27 ? 28 : 27);
        assertFalse(WalletSignatureVerifier.verifyPersonalSign(address(key), MESSAGE, flipped));
    }

    @Test
    void rejectsMalformedInputWithoutThrowing() throws Exception {
        ECKeyPair key = Keys.createEcKeyPair();
        String sig = sign(key, MESSAGE);
        assertFalse(WalletSignatureVerifier.verifyPersonalSign(null, MESSAGE, sig));
        assertFalse(WalletSignatureVerifier.verifyPersonalSign("0x1234", MESSAGE, sig));
        assertFalse(WalletSignatureVerifier.verifyPersonalSign(address(key), "", sig));
        assertFalse(WalletSignatureVerifier.verifyPersonalSign(address(key), "x".repeat(1025), sig));
        assertFalse(WalletSignatureVerifier.verifyPersonalSign(address(key), MESSAGE, "0x" + "00".repeat(65)));
        assertFalse(WalletSignatureVerifier.verifyPersonalSign(address(key), MESSAGE, sig.substring(0, 100)));
    }
}
