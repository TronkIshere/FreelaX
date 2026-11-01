package com.freelax.avalanchegateway.signer;

import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.env.EnvironmentGuardException;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tests: signer creation is refused outside LOCAL_SIMULATION, whatever else looks valid. */
class DevSignerFactoryTest {

    private static ValidatedEnvironment local(long chainId) {
        return new ValidatedEnvironment(AvalancheEnvironment.LOCAL_SIMULATION, chainId, "anvil", "127.0.0.1");
    }

    private static String code(Supplier<?> action) {
        return assertThrows(EnvironmentGuardException.class, action::get).code();
    }

    @Test
    void localSimulationGetsOneDistinctSignerPerRole() {
        SignerRegistry registry = DevSignerFactory.createRegistry(local(31337));
        Set<String> addresses = new HashSet<>();
        for (SignerRole role : SignerRole.values()) {
            Signer signer = registry.get(role);
            assertEquals(role, signer.role());
            assertEquals(AvalancheEnvironment.LOCAL_SIMULATION, signer.environment());
            assertEquals(31337L, signer.chainId());
            assertTrue(addresses.add(signer.address()), "no two roles may share a key: " + role);
        }
        assertEquals(SignerRole.values().length, addresses.size());
    }

    @Test
    void aLocalForkOfFujiIsAcceptedAsLocalSimulationOnly() {
        Signer signer = DevSignerFactory.create(local(43113), SignerRole.DEPLOYER);
        assertEquals(AvalancheEnvironment.LOCAL_SIMULATION, signer.environment());
    }

    @Test
    void creationIsRefusedInAvalancheFujiForEveryRole() {
        ValidatedEnvironment fuji = new ValidatedEnvironment(AvalancheEnvironment.AVALANCHE_FUJI, 43113, "avalanchego", "api.avax-test.network");
        for (SignerRole role : SignerRole.values()) {
            assertEquals("SIGNER_FORBIDDEN_ENVIRONMENT", code(() -> DevSignerFactory.create(fuji, role)));
        }
        assertEquals("SIGNER_FORBIDDEN_ENVIRONMENT", code(() -> DevSignerFactory.createRegistry(fuji)));
    }

    @Test
    void creationIsRefusedWithoutAValidatedEnvironment() {
        assertEquals("ENVIRONMENT_MISSING", code(() -> DevSignerFactory.create(null, SignerRole.ADMIN)));
        assertEquals("ENVIRONMENT_MISSING", code(() -> DevSignerFactory.create(
                new ValidatedEnvironment(null, 31337, "anvil", "127.0.0.1"), SignerRole.ADMIN)));
    }

    @Test
    void mainnetAndUnlistedChainIdsCannotGetASigner() {
        for (long id : new long[]{43114, 1, 0}) {
            assertEquals("REJECTED_MAINNET_CHAIN_ID", code(() -> DevSignerFactory.create(local(id), SignerRole.AUTHORIZATION)));
        }
        assertEquals("CHAIN_ID_NOT_ALLOWED", code(() -> DevSignerFactory.create(local(999), SignerRole.AUTHORIZATION)));
    }

    @Test
    void creationRequiresALocalAnvilFamilyNode() {
        ValidatedEnvironment wrongFamily = new ValidatedEnvironment(AvalancheEnvironment.LOCAL_SIMULATION, 31337, "avalanchego", "127.0.0.1");
        assertEquals("CLIENT_FAMILY_MISMATCH", code(() -> DevSignerFactory.create(wrongFamily, SignerRole.RELAYER)));
    }
}
