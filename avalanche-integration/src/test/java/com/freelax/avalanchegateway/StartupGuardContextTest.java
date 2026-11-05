package com.freelax.avalanchegateway;

import com.freelax.avalanchegateway.applicationException.EnvironmentGuardException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Real Spring contexts. Every failing configuration must abort startup; every rejection that is network-free sends nothing. */
class StartupGuardContextTest {

    private static final String MAINNET = "https://api.avax.network/ext/bc/C/rpc";
    private static final String FUJI = "https://api.avax-test.network/ext/bc/C/rpc";

    private static Throwable startupFailure(String... args) {
        List<String> all = new ArrayList<>(List.of("--server.port=0", "--avalanche.rpc.min-gap-ms=0"));
        all.addAll(List.of(args));
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AvalancheGatewayApplication.class)
                .web(WebApplicationType.NONE).run(all.toArray(String[]::new))) {
            fail("startup must fail but the context started: " + ctx.getId());
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private static String guardCode(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof EnvironmentGuardException g) {
                return g.code();
            }
        }
        return "NOT_A_GUARD_FAILURE: " + failure;
    }

    private static boolean failedWhileCreatingChainClient(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof BeanCreationException b && "chainClient".equals(b.getBeanName())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void missingEnvironmentFailsStartup() {
        assertEquals("ENVIRONMENT_MISSING",
                guardCode(startupFailure("--avalanche.rpc.primary.url=http://127.0.0.1:1")));
    }

    @Test
    void unknownEnvironmentValueIsRejectedIncludingMainnet() {
        for (String value : new String[]{"MAINNET", "AVALANCHE_MAINNET", "PRODUCTION", "fuji2"}) {
            assertNotNull(startupFailure("--avalanche.environment=" + value, "--avalanche.rpc.primary.url=http://127.0.0.1:1"));
        }
    }

    @Test
    void mainnetEndpointIsRejectedBeforeAnyClientIsBuilt() {
        for (String env : new String[]{"LOCAL_SIMULATION", "AVALANCHE_FUJI"}) {
            long start = System.nanoTime();
            Throwable failure = startupFailure("--avalanche.environment=" + env, "--avalanche.rpc.primary.url=" + MAINNET);
            assertEquals("REJECTED_MAINNET_ENDPOINT", guardCode(failure));
            assertTrue(failedWhileCreatingChainClient(failure), "rejected while the client bean is created, i.e. before Web3j exists");
            assertTrue((System.nanoTime() - start) / 1_000_000 < 15_000, "no network wait occurred");
        }
    }

    @Test
    void environmentEndpointMismatchesAreRejectedWithoutNetwork() throws Exception {
        try (StubRpcServer stub = StubRpcServer.start(43113, "anvil/v1.8.7")) {
            assertEquals("FUJI_FORBIDS_LOCAL_ENDPOINT",
                    guardCode(startupFailure("--avalanche.environment=AVALANCHE_FUJI", "--avalanche.rpc.primary.url=" + stub.url())));
            assertEquals(0, stub.requestCount(), "the loopback endpoint must not be contacted under AVALANCHE_FUJI");
        }
        assertEquals("LOCAL_REQUIRES_LOCAL_ENDPOINT",
                guardCode(startupFailure("--avalanche.environment=LOCAL_SIMULATION", "--avalanche.rpc.primary.url=" + FUJI)));
    }

    @Test
    void mainnetAndUnlistedChainIdsFailStartup() throws Exception {
        long[] rejected = {43114, 1, 0};
        for (long id : rejected) {
            try (StubRpcServer stub = StubRpcServer.start(id, "anvil/v1.8.7")) {
                assertEquals("REJECTED_MAINNET_CHAIN_ID", guardCode(startupFailure(
                        "--avalanche.environment=LOCAL_SIMULATION", "--avalanche.rpc.primary.url=" + stub.url())));
            }
        }
        try (StubRpcServer stub = StubRpcServer.start(999, "anvil/v1.8.7")) {
            assertEquals("CHAIN_ID_NOT_ALLOWED", guardCode(startupFailure(
                    "--avalanche.environment=LOCAL_SIMULATION", "--avalanche.rpc.primary.url=" + stub.url())));
        }
    }

    @Test
    void clientFamilyMismatchFailsStartup() throws Exception {
        try (StubRpcServer stub = StubRpcServer.start(31337, "avalanchego/1.15.2")) {
            assertEquals("CLIENT_FAMILY_MISMATCH", guardCode(startupFailure(
                    "--avalanche.environment=LOCAL_SIMULATION", "--avalanche.rpc.primary.url=" + stub.url())));
        }
    }

    @Test
    void secondaryProviderWithAnotherChainIdFailsStartup() throws Exception {
        try (StubRpcServer primary = StubRpcServer.start(31337, "anvil/v1.8.7");
             StubRpcServer secondary = StubRpcServer.start(43113, "anvil/v1.8.7")) {
            assertEquals("SECONDARY_PROVIDER_MISMATCH", guardCode(startupFailure(
                    "--avalanche.environment=LOCAL_SIMULATION",
                    "--avalanche.rpc.primary.url=" + primary.url(),
                    "--avalanche.rpc.secondary.url=" + secondary.url())));
        }
    }

    @Test
    void mainnetSecondaryEndpointFailsStartup() throws Exception {
        try (StubRpcServer primary = StubRpcServer.start(31337, "anvil/v1.8.7")) {
            assertEquals("REJECTED_MAINNET_ENDPOINT", guardCode(startupFailure(
                    "--avalanche.environment=LOCAL_SIMULATION",
                    "--avalanche.rpc.primary.url=" + primary.url(),
                    "--avalanche.rpc.secondary.url=" + MAINNET)));
        }
    }

    @Test
    void unreachableOrThrottledEndpointFailsClosedAndIsNotTreatedAsValid() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        Throwable unreachable = startupFailure("--avalanche.environment=LOCAL_SIMULATION",
                "--avalanche.rpc.primary.url=http://127.0.0.1:" + closedPort);
        assertTrue(String.valueOf(rootMessage(unreachable)).contains("could not be verified"), rootMessage(unreachable));

        try (StubRpcServer throttled = StubRpcServer.start(31337, "anvil/v1.8.7", true)) {
            Throwable failure = startupFailure("--avalanche.environment=LOCAL_SIMULATION",
                    "--avalanche.rpc.primary.url=" + throttled.url());
            assertTrue(rootMessage(failure).contains("could not be verified"), rootMessage(failure));
        }
    }

    @Test
    void aLocalForkOfFujiIsAcceptedOnlyAsLocalSimulation() throws Exception {
        try (StubRpcServer fork = StubRpcServer.start(43113, "anvil/v1.8.7");
             ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AvalancheGatewayApplication.class)
                     .web(WebApplicationType.NONE)
                     .run("--server.port=0", "--avalanche.rpc.min-gap-ms=0",
                             "--avalanche.environment=LOCAL_SIMULATION", "--avalanche.rpc.primary.url=" + fork.url())) {
            assertTrue(ctx.isActive());
        }
    }

    private static String rootMessage(Throwable failure) {
        Throwable t = failure;
        String message = String.valueOf(t.getMessage());
        while (t != null) {
            if (t.getMessage() != null && t.getMessage().contains("could not be verified")) {
                return t.getMessage();
            }
            t = t.getCause();
        }
        return message;
    }
}
