package com.freelax.solanagateway.solana;

import org.p2p.solanaj.core.PublicKey;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HexFormat;

final class BorshReader {

    private final byte[] data;
    private int offset;

    BorshReader(byte[] data, String accountName) {
        byte[] expected = SolanaValueCodec.discriminator("account", accountName);
        if (data.length < 8 || !Arrays.equals(expected, Arrays.copyOf(data, 8))) {
            throw new IllegalArgumentException("Invalid Anchor discriminator for " + accountName);
        }
        this.data = data;
        this.offset = 8;
    }

    String u64() {
        byte[] littleEndian = bytes(8);
        byte[] bigEndian = new byte[8];
        for (int i = 0; i < 8; i++) {
            bigEndian[i] = littleEndian[7 - i];
        }
        return new BigInteger(1, bigEndian).toString();
    }

    String i64() {
        return Long.toString(ByteBuffer.wrap(bytes(8)).order(ByteOrder.LITTLE_ENDIAN).getLong());
    }

    String publicKey() {
        return new PublicKey(bytes(32)).toBase58();
    }

    String hash32() {
        return HexFormat.of().formatHex(bytes(32));
    }

    boolean bool() {
        int value = u8();
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("Invalid Borsh boolean");
        }
        return value == 1;
    }

    int u8() {
        require(1);
        return data[offset++] & 0xff;
    }

    String optionI64() {
        return optionPresent() ? i64() : null;
    }

    String optionHash32() {
        return optionPresent() ? hash32() : null;
    }

    String optionPublicKey() {
        return optionPresent() ? publicKey() : null;
    }

    private boolean optionPresent() {
        int tag = u8();
        if (tag != 0 && tag != 1) {
            throw new IllegalArgumentException("Invalid Borsh option tag");
        }
        return tag == 1;
    }

    private byte[] bytes(int length) {
        require(length);
        byte[] value = Arrays.copyOfRange(data, offset, offset + length);
        offset += length;
        return value;
    }

    private void require(int length) {
        if (offset + length > data.length) {
            throw new IllegalArgumentException("Truncated Anchor account data");
        }
    }
}
