package com.freelax.avalanchegateway.signer;

import com.freelax.avalanchegateway.AvalancheGatewayApplication;
import com.freelax.avalanchegateway.StubRpcServer;
import com.freelax.avalanchegateway.applicationException.EnvironmentGuardException;
import com.freelax.avalanchegateway.chain.ChainClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(OutputCaptureExtension.class)
class SignerStartupContextTest {

    private static final String FUJI_URL = "https://api.avax-test.network/ext/bc/C/rpc";

    /** Plain class (not scanned, no stereotype): replaces the RPC client so we can prove no network call happens. */
    static class CountingChainConfig {
        static ChainClient client;

        @Bean
        @Primary
        ChainClient countingChainClient() {
            client = Mockito.mock(ChainClient.class);
            return client;
        }
    }

    private static Throwable failure(Class<?>[] sources, String... args) {
        String[] all = new String[args.length + 2];
        all[0] = "--server.port=0";
        all[1] = "--avalanche.rpc.min-gap-ms=0";
        System.arraycopy(args, 0, all, 2, args.length);
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(sources).web(WebApplicationType.NONE).run(all)) {
            fail("startup must fail");
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

    @Test
    void enablingSignersInFujiAbortsStartupBeforeAnyNetworkCall() {
        Throwable failure = failure(new Class<?>[]{AvalancheGatewayApplication.class, CountingChainConfig.class},
                "--avalanche.environment=AVALANCHE_FUJI", "--avalanche.rpc.primary.url=" + FUJI_URL,
                "--avalanche.signers.enabled=true");
        assertEquals("SIGNER_FORBIDDEN_ENVIRONMENT", guardCode(failure));
        // shutdown() is only Spring's destroy callback when the failed context closes; no RPC method may have been called.
        Mockito.verify(CountingChainConfig.client, Mockito.never()).chainId();
        Mockito.verify(CountingChainConfig.client, Mockito.never()).clientVersion();
    }

    @Test
    void enablingSignersWithoutAnEnvironmentFailsStartup() {
        assertEquals("ENVIRONMENT_MISSING", guardCode(failure(new Class<?>[]{AvalancheGatewayApplication.class},
                "--avalanche.rpc.primary.url=http://127.0.0.1:1", "--avalanche.signers.enabled=true")));
    }

    @Test
    void signersAreOffByDefaultAndNoKeyIsGenerated() throws Exception {
        try (StubRpcServer stub = StubRpcServer.start(31337, "anvil/v1.8.7");
             ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AvalancheGatewayApplication.class)
                     .web(WebApplicationType.NONE)
                     .run("--server.port=0", "--avalanche.rpc.min-gap-ms=0",
                             "--avalanche.environment=LOCAL_SIMULATION", "--avalanche.rpc.primary.url=" + stub.url())) {
            assertTrue(ctx.getBeansOfType(SignerRegistry.class).isEmpty());
        }
    }

    @Test
    void enabledInLocalSimulationCreatesDistinctSignersAndLogsAddressesOnly(CapturedOutput output) throws Exception {
        try (StubRpcServer stub = StubRpcServer.start(31337, "anvil/v1.8.7");
             ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AvalancheGatewayApplication.class)
                     .web(WebApplicationType.NONE)
                     .run("--server.port=0", "--avalanche.rpc.min-gap-ms=0",
                             "--avalanche.environment=LOCAL_SIMULATION", "--avalanche.rpc.primary.url=" + stub.url(),
                             "--avalanche.signers.enabled=true")) {
            SignerRegistry registry = ctx.getBean(SignerRegistry.class);
            Map<SignerRole, String> addresses = registry.addresses();
            assertEquals(SignerRole.values().length, addresses.size());
            assertEquals(addresses.size(), new HashSet<>(addresses.values()).size(), "one distinct key per role");
            assertTrue(stub.methods().stream().allMatch(m -> m.equals("eth_chainId") || m.equals("web3_clientVersion")),
                    "only read-only identity calls: " + stub.methods());

            String log = output.getAll().toLowerCase();
            for (SignerRole role : SignerRole.values()) {
                assertFalse(log.contains(DevSignerTest.privateKeyHex(registry.get(role))), "private key leaked into logs for " + role);
            }
            assertTrue(log.contains("ephemeral development signers created"));
            assertTrue(addresses.values().stream().allMatch(a -> log.contains(a.toLowerCase())), "addresses are logged");
        }
    }
}
