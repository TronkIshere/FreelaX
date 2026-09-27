package com.freelax.solanagateway.solana;

import org.p2p.solanaj.core.PublicKey;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class SolanaValueCodec {

    public static final BigInteger U64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    private SolanaValueCodec() {
    }

    public static PublicKey publicKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Public key is required");
        }
        PublicKey key = new PublicKey(value);
        if (key.toByteArray().length != 32) {
            throw new IllegalArgumentException("Invalid Solana public key");
        }
        return key;
    }

    public static BigInteger u64(String value, String field) {
        try {
            BigInteger number = new BigInteger(value);
            if (number.signum() < 0 || number.compareTo(U64_MAX) > 0) {
                throw new IllegalArgumentException(field + " must be between 0 and 2^64-1");
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be an unsigned decimal integer", exception);
        }
    }

    public static long i64(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a signed 64-bit decimal integer", exception);
        }
    }

    public static byte[] u64Le(String value, String field) {
        return u64Le(u64(value, field));
    }

    public static byte[] u64Le(BigInteger value) {
        byte[] result = new byte[8];
        byte[] bigEndian = value.toByteArray();
        for (int i = 0; i < 8 && i < bigEndian.length; i++) {
            result[i] = bigEndian[bigEndian.length - 1 - i];
        }
        return result;
    }

    public static byte[] i64Le(long value) {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
    }

    public static byte[] hash32(String value, String field, boolean rejectZero) {
        try {
            byte[] bytes = HexFormat.of().parseHex(value);
            if (bytes.length != 32) {
                throw new IllegalArgumentException(field + " must contain exactly 32 bytes");
            }
            if (rejectZero) {
                boolean allZero = true;
                for (byte item : bytes) {
                    allZero &= item == 0;
                }
                if (allZero) {
                    throw new IllegalArgumentException(field + " must not be all zero");
                }
            }
            return bytes;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " must be a 64-character hexadecimal string", exception);
        }
    }

    public static byte[] discriminator(String namespace, String name) {
        return java.util.Arrays.copyOf(sha256((namespace + ":" + name)
                .getBytes(StandardCharsets.UTF_8)), 8);
    }

    public static byte[] instructionData(String name, byte[]... arguments) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(discriminator("global", name));
        for (byte[] argument : arguments) {
            output.writeBytes(argument);
        }
        return output.toByteArray();
    }

    public static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static String sha256Hex(byte[] bytes) {
        return HexFormat.of().formatHex(sha256(bytes));
    }
}
