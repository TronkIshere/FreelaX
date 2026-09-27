package com.freelax.solanagateway.solana;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.utils.TweetNaclFast;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyTransactionCodecTest {

    private final LegacyTransactionCodec codec = new LegacyTransactionCodec();

    @Test
    void preservesSignerSlotsAndVerifiesPartialThenCompleteTransaction() {
        Account feePayer = new Account();
        Account user = new Account();
        TransactionInstruction instruction = new TransactionInstruction(
                SolanaAddresses.SYSTEM_PROGRAM,
                List.of(new AccountMeta(user.getPublicKey(), true, false)),
                new byte[]{9, 8, 7});

        LegacyTransactionCodec.CompiledTransaction compiled = codec.compile(
                feePayer.getPublicKey(), "11111111111111111111111111111111", List.of(instruction));

        byte[] payerSignature = sign(feePayer, compiled.message());
        LegacyTransactionCodec.ParsedTransaction partial = codec.parse(
                compiled.serialize(Map.of(feePayer.getPublicKey().toBase58(), payerSignature)));
        assertThat(partial.requiredSigners()).containsExactly(
                feePayer.getPublicKey().toBase58(), user.getPublicKey().toBase58());

        byte[] userSignature = sign(user, compiled.message());
        LegacyTransactionCodec.ParsedTransaction complete = codec.parse(compiled.serialize(Map.of(
                feePayer.getPublicKey().toBase58(), payerSignature,
                user.getPublicKey().toBase58(), userSignature)));
        complete.verifyAllSignatures();
        assertThat(complete.message()).isEqualTo(compiled.message());
    }

    private byte[] sign(Account account, byte[] message) {
        return new TweetNaclFast.Signature(new byte[0], account.getSecretKey()).detached(message);
    }
}
