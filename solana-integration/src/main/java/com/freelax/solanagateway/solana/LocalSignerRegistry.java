package com.freelax.solanagateway.solana;

import com.freelax.solanagateway.config.SolanaProperties;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.utils.TweetNaclFast;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class LocalSignerRegistry {

    private final Map<String, TransactionSigner> signers;

    public LocalSignerRegistry(SolanaProperties properties) {
        String configured = properties.localPrivateKeys();
        if (configured == null || configured.isBlank()) {
            this.signers = Map.of();
            return;
        }
        Map<String, TransactionSigner> loaded = new HashMap<>();
        Arrays.stream(configured.split(";"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Account::fromBase58PrivateKey)
                .map(LocalTransactionSigner::new)
                .forEach(signer -> loaded.put(signer.publicKey(), signer));
        this.signers = Collections.unmodifiableMap(loaded);
    }

    public Optional<TransactionSigner> find(String publicKey) {
        return Optional.ofNullable(signers.get(publicKey));
    }

    private static final class LocalTransactionSigner implements TransactionSigner {
        private final Account account;

        private LocalTransactionSigner(Account account) {
            this.account = account;
        }

        @Override
        public String publicKey() {
            return account.getPublicKey().toBase58();
        }

        @Override
        public byte[] sign(byte[] message) {
            return new TweetNaclFast.Signature(new byte[0], account.getSecretKey()).detached(message);
        }
    }
}
