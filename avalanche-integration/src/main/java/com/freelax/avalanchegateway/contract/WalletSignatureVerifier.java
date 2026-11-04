package com.freelax.avalanchegateway.contract;

import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Verifies an EIP-191 {@code personal_sign} signature (version 0x45: "\x19Ethereum Signed Message:\n" + length + message),
 * the format standard wallets produce for {@code personal_sign}. Used by the marketplace to bind an EVM address to a user
 * (register A8): the marketplace issues a single-use challenge message, the user's own wallet signs it, and this verifier
 * recovers the signer. Verification only: no key is created, held or used here.
 */
public final class WalletSignatureVerifier {

    public static final int MAX_MESSAGE_BYTES = 1024;
    private static final Pattern ADDRESS = Pattern.compile("0x[0-9a-fA-F]{40}");
    private static final Pattern SIGNATURE = Pattern.compile("0x[0-9a-fA-F]{130}");
    /** secp256k1 group order / 2: signatures with a higher s are malleable copies and are refused (EIP-2). */
    private static final BigInteger HALF_N = Sign.CURVE_PARAMS.getN().shiftRight(1);

    private WalletSignatureVerifier() {
    }

    public static boolean verifyPersonalSign(String address, String message, String signatureHex) {
        if (address == null || message == null || signatureHex == null
                || !ADDRESS.matcher(address).matches() || !SIGNATURE.matcher(signatureHex).matches()) {
            return false;
        }
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        if (messageBytes.length == 0 || messageBytes.length > MAX_MESSAGE_BYTES) {
            return false;
        }
        byte[] sig = Numeric.hexStringToByteArray(signatureHex);
        byte v = sig[64];
        if (v == 0 || v == 1) {
            v += 27;
        }
        if (v != 27 && v != 28) {
            return false;
        }
        byte[] r = Arrays.copyOfRange(sig, 0, 32);
        byte[] s = Arrays.copyOfRange(sig, 32, 64);
        if (new BigInteger(1, s).compareTo(HALF_N) > 0) {
            return false;
        }
        try {
            BigInteger key = Sign.signedPrefixedMessageToKey(messageBytes, new Sign.SignatureData(v, r, s));
            String recovered = "0x" + Keys.getAddress(key);
            return recovered.toLowerCase(Locale.ROOT).equals(address.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            return false;
        }
    }
}
