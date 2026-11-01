package com.freelax.solanagateway.solana;

import com.freelax.solanagateway.config.SolanaProperties;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.utils.TweetNaclFast;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class LocalSignerRegistry {

    private static final Set<String> LOCAL_RPC_HOSTS =
            Set.of("localhost", "127.0.0.1", "[::1]", "::1", "host.docker.internal");

    private final Map<String, TransactionSigner> signers;

    public LocalSignerRegistry(SolanaProperties properties) {
        String configured = properties.localPrivateKeys();
        if (configured == null || configured.isBlank()) {
            this.signers = Map.of();
            return;
        }
        requireLocalRpc(properties.rpcUrl());
        Map<String, TransactionSigner> loaded = new HashMap<>();
        Arrays.stream(configured.split(";"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Account::fromBase58PrivateKey)
                .map(LocalTransactionSigner::new)
                .forEach(signer -> loaded.put(signer.publicKey(), signer));
        this.signers = Collections.unmodifiableMap(loaded);
    }

    private static void requireLocalRpc(String rpcUrl) {
        String host;
        try {
            host = URI.create(rpcUrl).getHost();
        } catch (RuntimeException exception) {
            host = null;
        }
        if (host == null || !LOCAL_RPC_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "solana.local-private-keys is only allowed with a local validator RPC endpoint");
        }
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
