package com.freelax.avalanchegateway.contract;

import com.freelax.avalanchegateway.applicationException.EnvironmentGuardException;
import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Network-free manifest rules: parsing, environment and chain binding, asset allow-list and deny-list. */
class ManifestVerifierTest {

    private static final String ESCROW = "0x" + "e1".repeat(20);
    private static final String MOCK = "0x" + "a1".repeat(20);
    private static final ValidatedEnvironment LOCAL = new ValidatedEnvironment(AvalancheEnvironment.LOCAL_SIMULATION, 31337, "anvil", "127.0.0.1");
    private static final ValidatedEnvironment LOCAL_FORK = new ValidatedEnvironment(AvalancheEnvironment.LOCAL_SIMULATION, 43113, "anvil", "127.0.0.1");
    private static final ValidatedEnvironment FUJI = new ValidatedEnvironment(AvalancheEnvironment.AVALANCHE_FUJI, 43113, "avalanchego", "api.avax-test.network");

    private static DeploymentManifest manifest(String environment, long chainId, String token, String kind) {
        return new DeploymentManifest(1, environment, chainId, ESCROW, token, kind, MOCK, MOCK, MOCK, "1", 0);
    }

    private static String code(DeploymentManifest m, ValidatedEnvironment v) {
        return assertThrows(EnvironmentGuardException.class, () -> ManifestVerifier.checkStatic(m, v)).code();
    }

    @Test
    void acceptedCombinations() {
        ManifestVerifier.checkStatic(manifest("LOCAL_SIMULATION", 31337, MOCK, "MOCK"), LOCAL);
        ManifestVerifier.checkStatic(manifest("LOCAL_SIMULATION", 43113, ManifestVerifier.FUJI_USDC, "CIRCLE_FUJI_USDC"), LOCAL_FORK);
        ManifestVerifier.checkStatic(manifest("AVALANCHE_FUJI", 43113, ManifestVerifier.FUJI_USDC, "CIRCLE_FUJI_USDC"), FUJI);
    }

    @Test
    void environmentAndChainMustEqualTheVerifiedEndpoint() {
        assertEquals("MANIFEST_ENVIRONMENT_MISMATCH", code(manifest("AVALANCHE_FUJI", 43113, ManifestVerifier.FUJI_USDC, "CIRCLE_FUJI_USDC"), LOCAL_FORK));
        assertEquals("MANIFEST_ENVIRONMENT_MISMATCH", code(manifest("LOCAL_SIMULATION", 43113, MOCK, "MOCK"), FUJI));
        assertEquals("MANIFEST_CHAIN_MISMATCH", code(manifest("LOCAL_SIMULATION", 43113, MOCK, "MOCK"), LOCAL));
        assertEquals("MANIFEST_CHAIN_MISMATCH", code(manifest("AVALANCHE_FUJI", 31337, ManifestVerifier.FUJI_USDC, "CIRCLE_FUJI_USDC"), FUJI));
    }

    @Test
    void theMainnetUsdcAndTheBridgedUsdcEAreDenyListedWhateverTheEnvironment() {
        for (String denied : new String[]{"0xB97EF9Ef8734C71904D8002F8b6Bc66Dd9c48a6E", "0xA7D7079b0FEaD91F3e65f86E8915Cb59c1a4C664"}) {
            assertEquals("ASSET_DENY_LISTED", code(manifest("LOCAL_SIMULATION", 31337, denied.toLowerCase(), "MOCK"), LOCAL));
            assertEquals("ASSET_DENY_LISTED", code(manifest("AVALANCHE_FUJI", 43113, denied.toLowerCase(), "CIRCLE_FUJI_USDC"), FUJI));
        }
    }

    @Test
    void fujiAcceptsOnlyTheCircleFujiUsdcAndTheMockExistsOnlyLocally() {
        assertEquals("MOCK_TOKEN_FORBIDDEN", code(manifest("AVALANCHE_FUJI", 43113, MOCK, "MOCK"), FUJI));
        assertEquals("ASSET_NOT_ALLOWED", code(manifest("AVALANCHE_FUJI", 43113, MOCK, "CIRCLE_FUJI_USDC"), FUJI));
        assertEquals("ASSET_NOT_ALLOWED", code(manifest("LOCAL_SIMULATION", 31337, ManifestVerifier.FUJI_USDC, "CIRCLE_FUJI_USDC"), LOCAL),
                "the Circle token is only valid on chain 43113");
        assertEquals("ASSET_NOT_ALLOWED", code(manifest("LOCAL_SIMULATION", 31337, MOCK, "WRAPPED"), LOCAL));
    }

    @Test
    void parsingRejectsIncompleteOrMalformedManifests() {
        String good = "{\"schemaVersion\":1,\"environment\":\"LOCAL_SIMULATION\",\"chainId\":31337,\"escrow\":\"" + ESCROW + "\",\"token\":\"" + MOCK
                + "\",\"tokenKind\":\"MOCK\",\"admin\":\"" + MOCK + "\",\"arbiter\":\"" + MOCK + "\",\"authSigner\":\"" + MOCK + "\",\"startBlock\":7}";
        assertEquals(7, DeploymentManifest.parse(good).startBlock());
        for (String bad : new String[]{"not json", "{}", good.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                good.replace(ESCROW, "0x123"), good.replace("\"tokenKind\":\"MOCK\",", ""), good.replace("\"chainId\":31337,", "")}) {
            assertEquals("MANIFEST_INVALID", assertThrows(EnvironmentGuardException.class, () -> DeploymentManifest.parse(bad)).code(), bad);
        }
    }
}
