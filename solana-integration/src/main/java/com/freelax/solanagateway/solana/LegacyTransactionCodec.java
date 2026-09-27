package com.freelax.solanagateway.solana;

import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.utils.Base58;
import org.p2p.solanaj.utils.TweetNaclFast;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LegacyTransactionCodec {

    public record CompiledTransaction(byte[] message, List<String> requiredSigners) {
        public byte[] serialize(Map<String, byte[]> signatures) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.writeBytes(shortVec(requiredSigners.size()));
            for (String signer : requiredSigners) {
                byte[] signature = signatures.getOrDefault(signer, new byte[64]);
                if (signature.length != 64) {
                    throw new IllegalArgumentException("Ed25519 signature must contain 64 bytes");
                }
                output.writeBytes(signature);
            }
            output.writeBytes(message);
            return output.toByteArray();
        }
    }

    public record ParsedTransaction(byte[] message, List<String> requiredSigners,
                                    List<byte[]> signatures) {
        public void verifyAllSignatures() {
            for (int index = 0; index < requiredSigners.size(); index++) {
                byte[] signature = signatures.get(index);
                boolean zero = true;
                for (byte value : signature) {
                    zero &= value == 0;
                }
                if (zero) {
                    throw new IllegalArgumentException("Missing signature for " + requiredSigners.get(index));
                }
                PublicKey key = new PublicKey(requiredSigners.get(index));
                boolean valid = new TweetNaclFast.Signature(key.toByteArray(), new byte[0])
                        .detached_verify(message, signature);
                if (!valid) {
                    throw new IllegalArgumentException("Invalid signature for " + requiredSigners.get(index));
                }
            }
        }
    }

    public CompiledTransaction compile(PublicKey feePayer, String recentBlockhash,
                                       List<TransactionInstruction> instructions) {
        if (instructions.isEmpty()) {
            throw new IllegalArgumentException("At least one instruction is required");
        }

        LinkedHashMap<PublicKey, KeyMeta> merged = new LinkedHashMap<>();
        merge(merged, new AccountMeta(feePayer, true, true), false);
        for (TransactionInstruction instruction : instructions) {
            for (AccountMeta account : instruction.getKeys()) {
                merge(merged, account, false);
            }
            merge(merged, new AccountMeta(instruction.getProgramId(), false, false), true);
        }

        List<KeyMeta> keys = new ArrayList<>(merged.values());
        keys.sort(Comparator.comparingInt(key -> rank(key, feePayer)));
        if (keys.size() > 256) {
            throw new IllegalArgumentException("Legacy transaction cannot contain more than 256 accounts");
        }

        Map<PublicKey, Integer> indexes = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            indexes.put(keys.get(i).publicKey, i);
        }

        int required = (int) keys.stream().filter(key -> key.signer).count();
        int readonlySigned = (int) keys.stream().filter(key -> key.signer && !key.writable).count();
        int readonlyUnsigned = (int) keys.stream().filter(key -> !key.signer && !key.writable).count();

        ByteArrayOutputStream message = new ByteArrayOutputStream();
        message.write(required);
        message.write(readonlySigned);
        message.write(readonlyUnsigned);
        message.writeBytes(shortVec(keys.size()));
        keys.forEach(key -> message.writeBytes(key.publicKey.toByteArray()));
        byte[] blockhash = Base58.decode(recentBlockhash);
        if (blockhash.length != 32) {
            throw new IllegalArgumentException("Recent blockhash must decode to 32 bytes");
        }
        message.writeBytes(blockhash);
        message.writeBytes(shortVec(instructions.size()));
        for (TransactionInstruction instruction : instructions) {
            Integer programIndex = indexes.get(instruction.getProgramId());
            message.write(programIndex);
            message.writeBytes(shortVec(instruction.getKeys().size()));
            for (AccountMeta account : instruction.getKeys()) {
                message.write(indexes.get(account.getPublicKey()));
            }
            message.writeBytes(shortVec(instruction.getData().length));
            message.writeBytes(instruction.getData());
        }

        List<String> signers = keys.stream().filter(key -> key.signer)
                .map(key -> key.publicKey.toBase58()).toList();
        return new CompiledTransaction(message.toByteArray(), signers);
    }

    public ParsedTransaction parse(byte[] serialized) {
        Cursor cursor = new Cursor(serialized);
        int signatureCount = cursor.shortVec();
        List<byte[]> signatures = new ArrayList<>(signatureCount);
        for (int i = 0; i < signatureCount; i++) {
            signatures.add(cursor.bytes(64));
        }
        byte[] message = cursor.remaining();
        if (message.length < 4) {
            throw new IllegalArgumentException("Truncated Solana transaction message");
        }
        int required = message[0] & 0xff;
        if (required != signatureCount) {
            throw new IllegalArgumentException("Signature count does not match message header");
        }
        Cursor messageCursor = new Cursor(message, 3);
        int accountCount = messageCursor.shortVec();
        if (required > accountCount) {
            throw new IllegalArgumentException("Required signer count exceeds account count");
        }
        List<String> signers = new ArrayList<>(required);
        for (int i = 0; i < accountCount; i++) {
            byte[] key = messageCursor.bytes(32);
            if (i < required) {
                signers.add(new PublicKey(key).toBase58());
            }
        }
        return new ParsedTransaction(message, List.copyOf(signers), List.copyOf(signatures));
    }

    private void merge(Map<PublicKey, KeyMeta> keys, AccountMeta account, boolean invoked) {
        keys.compute(account.getPublicKey(), (ignored, current) -> current == null
                ? new KeyMeta(account.getPublicKey(), account.isSigner(), account.isWritable(), invoked)
                : new KeyMeta(current.publicKey, current.signer || account.isSigner(),
                    current.writable || account.isWritable(), current.invoked || invoked));
    }

    private int rank(KeyMeta key, PublicKey feePayer) {
        if (key.publicKey.equals(feePayer)) return 0;
        if (key.signer && key.writable) return 1;
        if (key.signer) return 2;
        if (key.writable) return 3;
        return 4;
    }

    static byte[] shortVec(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("ShortVec length cannot be negative");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int remaining = value;
        do {
            int next = remaining & 0x7f;
            remaining >>>= 7;
            output.write(remaining == 0 ? next : next | 0x80);
        } while (remaining != 0);
        return output.toByteArray();
    }

    private record KeyMeta(PublicKey publicKey, boolean signer, boolean writable, boolean invoked) {
    }

    private static final class Cursor {
        private final byte[] data;
        private int offset;

        private Cursor(byte[] data) {
            this(data, 0);
        }

        private Cursor(byte[] data, int offset) {
            this.data = data;
            this.offset = offset;
        }

        private int shortVec() {
            int value = 0;
            int shift = 0;
            for (int i = 0; i < 3; i++) {
                int next = unsignedByte();
                value |= (next & 0x7f) << shift;
                if ((next & 0x80) == 0) {
                    return value;
                }
                shift += 7;
            }
            throw new IllegalArgumentException("Invalid ShortVec value");
        }

        private int unsignedByte() {
            require(1);
            return data[offset++] & 0xff;
        }

        private byte[] bytes(int length) {
            require(length);
            byte[] value = Arrays.copyOfRange(data, offset, offset + length);
            offset += length;
            return value;
        }

        private byte[] remaining() {
            return bytes(data.length - offset);
        }

        private void require(int length) {
            if (length < 0 || offset + length > data.length) {
                throw new IllegalArgumentException("Truncated Solana transaction");
            }
        }
    }
}
