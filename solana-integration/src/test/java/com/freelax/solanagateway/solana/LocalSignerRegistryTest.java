package com.freelax.solanagateway.solana;

import com.freelax.solanagateway.config.SolanaProperties;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.utils.Base58;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSignerRegistryTest {
    private static SolanaProperties properties(String rpcUrl, String keys) {
        return new SolanaProperties(rpcUrl, "program", "confirmed", "", "key", true, 60, keys);
    }

    @Test
    void rejectsLocalKeysForRemoteRpc() {
        String key = Base58.encode(new Account().getSecretKey());
        assertThrows(IllegalStateException.class,
                () -> new LocalSignerRegistry(properties("https://api.mainnet-beta.solana.com", key)));
        assertThrows(IllegalStateException.class,
                () -> new LocalSignerRegistry(properties("http://localhost.evil.example:9123", key)));
        assertThrows(IllegalStateException.class,
                () -> new LocalSignerRegistry(properties("not a url", key)));
    }

    @Test
    void loadsLocalKeysForLoopbackAndDockerHostRpc() {
        Account account = new Account();
        String key = Base58.encode(account.getSecretKey());
        for (String url : new String[]{"http://127.0.0.1:9123", "http://localhost:9123",
                "http://host.docker.internal:9123"}) {
            LocalSignerRegistry registry = new LocalSignerRegistry(properties(url, key));
            assertTrue(registry.find(account.getPublicKey().toBase58()).isPresent());
        }
    }

    @Test
    void remoteRpcIsFineWithoutLocalKeys() {
        LocalSignerRegistry registry = new LocalSignerRegistry(
                properties("https://api.mainnet-beta.solana.com", ""));
        assertEquals(false, registry.find("anything").isPresent());
    }
}
