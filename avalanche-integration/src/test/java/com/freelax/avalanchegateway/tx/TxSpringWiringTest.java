package com.freelax.avalanchegateway.tx;

import com.freelax.avalanchegateway.AvalancheGatewayApplication;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.signer.SignerRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real Spring wiring over the Testcontainers stack: persistence, Flyway, signers, submission and the periodic reconciler. */
@Testcontainers(disabledWithoutDocker = true)
class TxSpringWiringTest {

    @BeforeAll
    static void start() {
        LocalStack.start();
        AnvilRpc.setCode(LocalStack.anvilUrl(), "0x1111111111111111111111111111111111111111", "0x00");
    }

    @Test
    void beansAreWiredMigrationsRunAndThePeriodicReconcilerConfirmsWithoutAManualCall() throws Exception {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AvalancheGatewayApplication.class)
                .web(WebApplicationType.NONE)
                .run("--server.port=0", "--avalanche.rpc.min-gap-ms=0",
                        "--avalanche.environment=LOCAL_SIMULATION", "--avalanche.rpc.primary.url=" + LocalStack.anvilUrl(),
                        "--avalanche.signers.enabled=true", "--avalanche.tx.enabled=true",
                        "--avalanche.tx.reconcile-interval-ms=200",
                        "--avalanche.db.url=" + LocalStack.jdbcUrl(), "--avalanche.db.username=" + LocalStack.dbUser(),
                        "--avalanche.db.password=" + LocalStack.dbPassword())) {
            SignerRegistry registry = ctx.getBean(SignerRegistry.class);
            AnvilRpc.fund(LocalStack.anvilUrl(), registry.get(SignerRole.RELAYER).address());
            assertNotNull(ctx.getBean(TxTracker.class));
            assertNotNull(ctx.getBean(TxReconcileScheduler.class));
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'avalanche_gateway'"
                    + " AND table_name IN ('avalanche_tx','avalanche_nonce')", Integer.class), "Flyway V1 created the schema");

            SubmissionService submission = ctx.getBean(SubmissionService.class);
            TxRecord submitted = submission.submit(new SubmissionService.SubmitRequest("op-wiring", SignerRole.RELAYER,
                    "0x1111111111111111111111111111111111111111", "0x", 100_000));
            assertEquals(TxState.SUBMITTED, submitted.state());

            TxRepository repository = ctx.getBean(TxRepository.class);
            TxState state = submitted.state();
            for (int i = 0; i < 60 && state != TxState.CONFIRMED; i++) {
                Thread.sleep(200);
                state = repository.findById(submitted.id()).orElseThrow().state();
            }
            assertEquals(TxState.CONFIRMED, state, "the scheduler reconciled the record on its own");
            assertTrue(repository.findById(submitted.id()).orElseThrow().receiptStatus() == 1);
        }
    }
}
