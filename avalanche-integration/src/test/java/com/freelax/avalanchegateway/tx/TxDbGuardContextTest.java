package com.freelax.avalanchegateway.tx;

import com.freelax.avalanchegateway.AvalancheGatewayApplication;
import com.freelax.avalanchegateway.StubRpcServer;
import com.freelax.avalanchegateway.env.EnvironmentGuardException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Docker-free: persistence is opt-in, and when enabled the database settings fail closed. */
class TxDbGuardContextTest {

    private static Throwable failure(StubRpcServer stub, String... extra) {
        List<String> args = new ArrayList<>(List.of("--server.port=0", "--avalanche.rpc.min-gap-ms=0",
                "--avalanche.environment=LOCAL_SIMULATION", "--avalanche.rpc.primary.url=" + stub.url(),
                "--avalanche.tx.enabled=true"));
        args.addAll(List.of(extra));
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AvalancheGatewayApplication.class)
                .web(WebApplicationType.NONE).run(args.toArray(String[]::new))) {
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
    void persistenceIsOffByDefaultSoStartupNeedsNoDatabase() throws Exception {
        try (StubRpcServer stub = StubRpcServer.start(31337, "anvil/v1.8.7");
             ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AvalancheGatewayApplication.class)
                     .web(WebApplicationType.NONE)
                     .run("--server.port=0", "--avalanche.rpc.min-gap-ms=0", "--avalanche.environment=LOCAL_SIMULATION",
                             "--avalanche.rpc.primary.url=" + stub.url())) {
            assertTrue(ctx.getBeansOfType(TxTracker.class).isEmpty());
            assertTrue(ctx.getBeansOfType(SubmissionService.class).isEmpty());
            assertTrue(ctx.getBeansOfType(javax.sql.DataSource.class).isEmpty());
        }
    }

    @Test
    void enablingPersistenceWithoutADatabaseUrlFailsStartup() throws Exception {
        try (StubRpcServer stub = StubRpcServer.start(31337, "anvil/v1.8.7")) {
            assertEquals("DB_URL_MISSING", guardCode(failure(stub)));
        }
    }

    @Test
    void aRemoteHostOrAnotherFreelaXDatabaseIsRefusedBeforeAnyConnection() throws Exception {
        try (StubRpcServer stub = StubRpcServer.start(31337, "anvil/v1.8.7")) {
            assertEquals("DB_HOST_NOT_LOCAL", guardCode(failure(stub,
                    "--avalanche.db.url=jdbc:mysql://db.example.com:3306/avalanche_gateway", "--avalanche.db.username=u", "--avalanche.db.password=p")));
            assertEquals("DB_NAME_NOT_ALLOWED", guardCode(failure(stub,
                    "--avalanche.db.url=jdbc:mysql://localhost:3306/solana_gateway", "--avalanche.db.username=u", "--avalanche.db.password=p")));
            assertEquals("DB_NAME_NOT_ALLOWED", guardCode(failure(stub,
                    "--avalanche.db.url=jdbc:mysql://localhost:3306/marketplace", "--avalanche.db.username=u", "--avalanche.db.password=p")));
        }
    }
}
