package lab.phase1;

import lab.phase1.EnvGuard.Env;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Offline specification test for the experiment-level guard (no network). */
class EnvGuardTest {
    static String reason(Env e, String url, long chain) {
        try { EnvGuard.check(e, url, chain); return "OK"; } catch (IllegalStateException ex) { return ex.getMessage(); }
    }

    @Test
    void allowsOnlyTheTwoSupportedEnvironments() {
        assertEquals("OK", reason(Env.LOCAL_SIMULATION, "http://localhost:8545", 31337));
        assertEquals("OK", reason(Env.LOCAL_SIMULATION, "http://127.0.0.1:8546", 43113));      // local fork of Fuji
        assertEquals("OK", reason(Env.AVALANCHE_FUJI, "https://api.avax-test.network/ext/bc/C/rpc", 43113));
        assertEquals("OK", reason(Env.AVALANCHE_FUJI, "https://avalanche-fuji-c-chain-rpc.publicnode.com", 43113));
    }

    @Test
    void rejectsMainnetAndUnlistedChains() {
        assertTrue(reason(Env.AVALANCHE_FUJI, "https://api.avax-test.network/ext/bc/C/rpc", 43114).startsWith("REJECTED_MAINNET_CHAIN_ID"));
        assertTrue(reason(Env.LOCAL_SIMULATION, "http://localhost:8545", 43114).startsWith("REJECTED_MAINNET_CHAIN_ID"));
        assertTrue(reason(Env.LOCAL_SIMULATION, "http://localhost:8545", 1).startsWith("REJECTED_MAINNET_CHAIN_ID"));
        assertTrue(reason(Env.LOCAL_SIMULATION, "http://localhost:8545", 999).startsWith("LOCAL_CHAIN_ID_NOT_ALLOWED"));
        assertTrue(reason(Env.AVALANCHE_FUJI, "https://api.avax-test.network/ext/bc/C/rpc", 31337).startsWith("FUJI_CHAIN_ID_MISMATCH"));
        assertTrue(reason(Env.AVALANCHE_FUJI, "https://api.avax.network/ext/bc/C/rpc", 43113).startsWith("REJECTED_MAINNET_ENDPOINT"));
    }

    @Test
    void endpointPrecheckNeedsNoNetworkAndBlocksMainnetHost() {
        for (Env e : Env.values()) {
            try { EnvGuard.checkEndpoint(e, "https://api.avax.network/ext/bc/C/rpc"); fail("must reject"); }
            catch (IllegalStateException ex) { assertTrue(ex.getMessage().startsWith("REJECTED_MAINNET_ENDPOINT")); }
        }
    }

    @Test
    void rejectsEnvironmentEndpointMismatchWhichChainIdCannotDetect() {
        // a local fork reports 43113: only the endpoint class distinguishes it from real Fuji
        assertEquals("FUJI_FORBIDS_LOOPBACK_ENDPOINT", reason(Env.AVALANCHE_FUJI, "http://localhost:8546", 43113));
        assertTrue(reason(Env.LOCAL_SIMULATION, "https://api.avax-test.network/ext/bc/C/rpc", 43113).startsWith("LOCAL_REQUIRES_LOOPBACK_ENDPOINT"));
        assertEquals("BAD_ENDPOINT", reason(Env.LOCAL_SIMULATION, "not-a-url", 31337));
    }
}
