package com.freelax.avalanchegateway;

import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.env.EnvironmentGuard;
import com.freelax.avalanchegateway.env.EnvironmentGuardException;
import org.junit.jupiter.api.Test;

import static com.freelax.avalanchegateway.env.AvalancheEnvironment.AVALANCHE_FUJI;
import static com.freelax.avalanchegateway.env.AvalancheEnvironment.LOCAL_SIMULATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pure guard specification: no network, no Spring. */
class EnvironmentGuardTest {

    private static final String FUJI = "https://api.avax-test.network/ext/bc/C/rpc";

    private static String endpoint(AvalancheEnvironment env, String url) {
        try {
            EnvironmentGuard.checkEndpoint(env, url);
            return "OK";
        } catch (EnvironmentGuardException e) {
            return e.code();
        }
    }

    private static String chain(AvalancheEnvironment env, long id) {
        try {
            EnvironmentGuard.checkChainId(env, id);
            return "OK";
        } catch (EnvironmentGuardException e) {
            return e.code();
        }
    }

    @Test
    void acceptsOnlyTheSupportedEndpoints() {
        assertEquals("OK", endpoint(LOCAL_SIMULATION, "http://127.0.0.1:8545"));
        assertEquals("OK", endpoint(LOCAL_SIMULATION, "http://localhost:8545"));
        assertEquals("OK", endpoint(LOCAL_SIMULATION, "http://[::1]:8545"));
        assertEquals("OK", endpoint(LOCAL_SIMULATION, "http://host.docker.internal:8545"));
        assertEquals("OK", endpoint(LOCAL_SIMULATION, "http://anvil:8545"));
        assertEquals("OK", endpoint(AVALANCHE_FUJI, FUJI));
        assertEquals("OK", endpoint(AVALANCHE_FUJI, "https://avalanche-fuji-c-chain-rpc.publicnode.com"));
    }

    @Test
    void rejectsMainnetEndpointsInEveryEnvironment() {
        for (AvalancheEnvironment env : AvalancheEnvironment.values()) {
            assertEquals("REJECTED_MAINNET_ENDPOINT", endpoint(env, "https://api.avax.network/ext/bc/C/rpc"));
            assertEquals("REJECTED_MAINNET_ENDPOINT", endpoint(env, "https://API.AVAX.NETWORK./ext/bc/C/rpc"));
            assertEquals("REJECTED_MAINNET_ENDPOINT", endpoint(env, "https://some-provider.example/avalanche-mainnet/rpc"));
            assertEquals("REJECTED_MAINNET_ENDPOINT", endpoint(env, "https://some-provider.example/43114/rpc"));
        }
    }

    @Test
    void rejectsEnvironmentEndpointMismatch() {
        assertEquals("LOCAL_REQUIRES_LOCAL_ENDPOINT", endpoint(LOCAL_SIMULATION, FUJI));
        assertEquals("FUJI_FORBIDS_LOCAL_ENDPOINT", endpoint(AVALANCHE_FUJI, "https://127.0.0.1:8546"));
        assertEquals("FUJI_FORBIDS_LOCAL_ENDPOINT", endpoint(AVALANCHE_FUJI, "http://localhost:8546"));
        assertEquals("FUJI_REQUIRES_HTTPS", endpoint(AVALANCHE_FUJI, "http://api.avax-test.network/ext/bc/C/rpc"));
        assertEquals("FUJI_HOST_NOT_ALLOW_LISTED", endpoint(AVALANCHE_FUJI, "https://rpc.example.org"));
    }

    @Test
    void singleLabelHostnamesAreLocalOnlyAndNeverPassFujiValidation() {
        assertEquals("OK", endpoint(LOCAL_SIMULATION, "http://anvil:8545"));
        assertEquals("FUJI_FORBIDS_LOCAL_ENDPOINT", endpoint(AVALANCHE_FUJI, "https://anvil:8545"));
        assertEquals("FUJI_FORBIDS_LOCAL_ENDPOINT", endpoint(AVALANCHE_FUJI, "http://anvil:8545"));
        assertEquals("FUJI_FORBIDS_LOCAL_ENDPOINT", endpoint(AVALANCHE_FUJI, "https://host.docker.internal:8545"));
    }

    @Test
    void fujiAllowListIsAnExactHostMatch() {
        assertEquals("FUJI_HOST_NOT_ALLOW_LISTED", endpoint(AVALANCHE_FUJI, "https://api.avax-test.network.evil.example/rpc"));
        assertEquals("FUJI_HOST_NOT_ALLOW_LISTED", endpoint(AVALANCHE_FUJI, "https://evil.api.avax-test.network/rpc"));
        assertEquals("FUJI_HOST_NOT_ALLOW_LISTED", endpoint(AVALANCHE_FUJI, "https://avax-test.network/rpc"));
        assertEquals("OK", endpoint(AVALANCHE_FUJI, "https://API.AVAX-TEST.NETWORK/ext/bc/C/rpc"));
    }

    @Test
    void rejectsMalformedAndCredentialedEndpoints() {
        assertEquals("BAD_ENDPOINT", endpoint(LOCAL_SIMULATION, null));
        assertEquals("BAD_ENDPOINT", endpoint(LOCAL_SIMULATION, ""));
        assertEquals("BAD_ENDPOINT", endpoint(LOCAL_SIMULATION, "not a url"));
        assertEquals("BAD_ENDPOINT", endpoint(LOCAL_SIMULATION, "ftp://localhost:8545"));
        assertEquals("BAD_ENDPOINT", endpoint(LOCAL_SIMULATION, "localhost:8545"));
        assertEquals("ENDPOINT_CREDENTIALS_FORBIDDEN", endpoint(LOCAL_SIMULATION, "http://user:secret@localhost:8545"));
    }

    @Test
    void errorMessagesNeverContainTheUrlPathOrCredentials() {
        EnvironmentGuardException e = assertThrows(EnvironmentGuardException.class,
                () -> EnvironmentGuard.checkEndpoint(AVALANCHE_FUJI, "https://rpc.example.org/v1/SECRET-API-KEY"));
        assertEquals(false, e.getMessage().contains("SECRET-API-KEY"));
    }

    @Test
    void environmentHasNoDefault() {
        assertThrows(EnvironmentGuardException.class, () -> EnvironmentGuard.requireEnvironment(null));
        assertEquals("ENVIRONMENT_MISSING", assertThrows(EnvironmentGuardException.class,
                () -> EnvironmentGuard.checkEndpoint(null, FUJI)).code());
    }

    @Test
    void chainIdAllowListIsPerEnvironmentAndNeverIncludesMainnet() {
        assertEquals("OK", chain(LOCAL_SIMULATION, 31337));
        assertEquals("OK", chain(LOCAL_SIMULATION, 43113));
        assertEquals("OK", chain(AVALANCHE_FUJI, 43113));
        for (AvalancheEnvironment env : AvalancheEnvironment.values()) {
            assertEquals("REJECTED_MAINNET_CHAIN_ID", chain(env, 43114));
            assertEquals("REJECTED_MAINNET_CHAIN_ID", chain(env, 1));
            assertEquals("REJECTED_MAINNET_CHAIN_ID", chain(env, 0));
            assertEquals("CHAIN_ID_NOT_ALLOWED", chain(env, 999));
        }
        assertEquals("CHAIN_ID_NOT_ALLOWED", chain(AVALANCHE_FUJI, 31337));
    }

    @Test
    void clientFamilyDistinguishesALocalForkFromRealFuji() {
        assertEquals("anvil", EnvironmentGuard.checkClientVersion(LOCAL_SIMULATION, "anvil/v1.8.7"));
        assertEquals("avalanchego", EnvironmentGuard.checkClientVersion(AVALANCHE_FUJI, "avalanchego/1.15.2"));
        assertEquals("CLIENT_FAMILY_MISMATCH", assertThrows(EnvironmentGuardException.class,
                () -> EnvironmentGuard.checkClientVersion(AVALANCHE_FUJI, "anvil/v1.8.7")).code());
        assertEquals("CLIENT_FAMILY_MISMATCH", assertThrows(EnvironmentGuardException.class,
                () -> EnvironmentGuard.checkClientVersion(LOCAL_SIMULATION, "avalanchego/1.15.2")).code());
        assertEquals("CLIENT_FAMILY_MISMATCH", assertThrows(EnvironmentGuardException.class,
                () -> EnvironmentGuard.checkClientVersion(LOCAL_SIMULATION, null)).code());
    }

    @Test
    void secondaryProviderMustMatchThePrimary() {
        EnvironmentGuard.checkSecondaryMatchesPrimary(43113, "avalanchego", 43113, "avalanchego");
        assertEquals("SECONDARY_PROVIDER_MISMATCH", assertThrows(EnvironmentGuardException.class,
                () -> EnvironmentGuard.checkSecondaryMatchesPrimary(31337, "anvil", 43113, "anvil")).code());
        assertEquals("SECONDARY_PROVIDER_MISMATCH", assertThrows(EnvironmentGuardException.class,
                () -> EnvironmentGuard.checkSecondaryMatchesPrimary(43113, "anvil", 43113, "avalanchego")).code());
    }
}
