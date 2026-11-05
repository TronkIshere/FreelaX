package com.freelax.avalanchegateway.tx;

import com.freelax.avalanchegateway.AvalancheGatewayApplication;
import com.freelax.avalanchegateway.contract.EscrowCalls;
import com.freelax.avalanchegateway.dto.request.SubmitRequest;
import com.freelax.avalanchegateway.entity.TxRecord;
import com.freelax.avalanchegateway.entity.TxState;
import com.freelax.avalanchegateway.repository.TxRepository;
import com.freelax.avalanchegateway.scan.LogScanner;
import com.freelax.avalanchegateway.scan.ScanCoordinator;
import com.freelax.avalanchegateway.scan.WsHintListener;
import com.freelax.avalanchegateway.service.TransactionReconcileService;
import com.freelax.avalanchegateway.service.TransactionSubmissionService;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.signer.SignerRole;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    void scannerBeansAreWiredOnlyWhenEnabledAndRefuseAnInvalidContractAddress() throws Exception {
        String[] base = {"--server.port=0", "--avalanche.rpc.min-gap-ms=0", "--avalanche.environment=LOCAL_SIMULATION",
                "--avalanche.rpc.primary.url=" + LocalStack.anvilUrl(), "--avalanche.tx.enabled=true",
                "--avalanche.db.url=" + LocalStack.jdbcUrl(), "--avalanche.db.username=" + LocalStack.dbUser(),
                "--avalanche.db.password=" + LocalStack.dbPassword()};
        try (ConfigurableApplicationContext off = new SpringApplicationBuilder(AvalancheGatewayApplication.class)
                .web(WebApplicationType.NONE).run(base)) {
            assertTrue(off.getBeansOfType(LogScanner.class).isEmpty());
        }
        String[] invalid = Arrays.copyOf(base, base.length + 2);
        invalid[base.length] = "--avalanche.scan.enabled=true";
        invalid[base.length + 1] = "--avalanche.scan.contract-address=0x123";
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AvalancheGatewayApplication.class)
                .web(WebApplicationType.NONE).run(invalid)) {
            Assertions.fail("an invalid contract address must abort startup");
        } catch (Throwable expected) {
            boolean found = false;
            for (Throwable t = expected; t != null; t = t.getCause()) {
                found |= String.valueOf(t.getMessage()).contains("SCAN_CONTRACT_INVALID");
            }
            assertTrue(found, String.valueOf(expected));
        }
        String[] valid = Arrays.copyOf(base, base.length + 4);
        valid[base.length] = "--avalanche.scan.enabled=true";
        valid[base.length + 1] = "--avalanche.scan.contract-address=0x4444444444444444444444444444444444444444";
        valid[base.length + 2] = "--avalanche.scan.ws-url=" + LocalStack.anvilUrl().replaceFirst("^http://", "ws://");
        valid[base.length + 3] = "--avalanche.scan.interval-ms=500";
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AvalancheGatewayApplication.class)
                .web(WebApplicationType.NONE).run(valid)) {
            assertNotNull(ctx.getBean(LogScanner.class));
            assertNotNull(ctx.getBean(ScanCoordinator.class));
            assertNotNull(ctx.getBean(WsHintListener.class));
        }
    }

    private static String[] txArgs(String... extra) {
        List<String> args = new ArrayList<>(List.of("--server.port=0", "--avalanche.rpc.min-gap-ms=0",
                "--avalanche.environment=LOCAL_SIMULATION", "--avalanche.rpc.primary.url=" + LocalStack.anvilUrl(),
                "--avalanche.tx.enabled=true", "--avalanche.db.url=" + LocalStack.jdbcUrl(),
                "--avalanche.db.username=" + LocalStack.dbUser(), "--avalanche.db.password=" + LocalStack.dbPassword()));
        args.addAll(List.of(extra));
        return args.toArray(String[]::new);
    }

    private static boolean failedWith(Throwable failure, String code) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (String.valueOf(t.getMessage()).contains(code)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void submissionNeedsAVerifiedManifestAndRefusesOneForAnotherChain(@TempDir Path dir) throws Exception {
        ContractFixtures.Stack stack = ContractFixtures.deployStack(LocalStack.anvilUrl(), ContractFixtures.ANVIL_ACCOUNT_0);
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AvalancheGatewayApplication.class).web(WebApplicationType.NONE)
                .run(txArgs("--avalanche.signers.enabled=true"))) {
            Assertions.fail("signers and submission without a manifest must refuse to start");
        } catch (Throwable expected) {
            assertTrue(failedWith(expected, "MANIFEST_REQUIRED"), String.valueOf(expected));
        }
        Path wrongChain = ContractFixtures.writeManifest(dir.resolve("wrong-chain.json"), "LOCAL_SIMULATION", 43113, stack, 0);
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AvalancheGatewayApplication.class).web(WebApplicationType.NONE)
                .run(txArgs("--avalanche.deployment-manifest=" + wrongChain))) {
            Assertions.fail("a manifest for another chain must refuse to start");
        } catch (Throwable expected) {
            assertTrue(failedWith(expected, "MANIFEST_CHAIN_MISMATCH"), String.valueOf(expected));
        }
        Path mainnetToken = dir.resolve("mainnet-token.json");
        Files.writeString(mainnetToken, Files.readString(
                ContractFixtures.writeManifest(dir.resolve("base.json"), "LOCAL_SIMULATION", 31337, stack, 0))
                .replace(stack.usdc(), "0xb97ef9ef8734c71904d8002f8b6bc66dd9c48a6e"));
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AvalancheGatewayApplication.class).web(WebApplicationType.NONE)
                .run(txArgs("--avalanche.deployment-manifest=" + mainnetToken))) {
            Assertions.fail("the Mainnet USDC address must be refused");
        } catch (Throwable expected) {
            assertTrue(failedWith(expected, "ASSET_DENY_LISTED"), String.valueOf(expected));
        }
        Path mismatchScan = ContractFixtures.writeManifest(dir.resolve("scan.json"), "LOCAL_SIMULATION", 31337, stack, 0);
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AvalancheGatewayApplication.class).web(WebApplicationType.NONE)
                .run(txArgs("--avalanche.deployment-manifest=" + mismatchScan, "--avalanche.scan.enabled=true",
                        "--avalanche.scan.contract-address=0x4444444444444444444444444444444444444444"))) {
            Assertions.fail("a scan address that differs from the manifest must be refused");
        } catch (Throwable expected) {
            assertTrue(failedWith(expected, "SCAN_CONTRACT_MISMATCH"), String.valueOf(expected));
        }
    }

    @Test
    void beansAreWiredMigrationsRunAndThePeriodicReconcilerConfirmsWithoutAManualCall(@TempDir Path dir) throws Exception {
        ContractFixtures.Stack stack = ContractFixtures.deployStack(LocalStack.anvilUrl(), ContractFixtures.ANVIL_ACCOUNT_0);
        Path manifest = ContractFixtures.writeManifest(dir.resolve("manifest.json"), "LOCAL_SIMULATION", 31337, stack, 0);
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AvalancheGatewayApplication.class)
                .web(WebApplicationType.NONE)
                .run("--server.port=0", "--avalanche.rpc.min-gap-ms=0", "--avalanche.deployment-manifest=" + manifest,
                        "--avalanche.environment=LOCAL_SIMULATION", "--avalanche.rpc.primary.url=" + LocalStack.anvilUrl(),
                        "--avalanche.signers.enabled=true", "--avalanche.tx.enabled=true",
                        "--avalanche.tx.reconcile-interval-ms=200",
                        "--avalanche.db.url=" + LocalStack.jdbcUrl(), "--avalanche.db.username=" + LocalStack.dbUser(),
                        "--avalanche.db.password=" + LocalStack.dbPassword())) {
            SignerRegistry registry = ctx.getBean(SignerRegistry.class);
            AnvilRpc.fund(LocalStack.anvilUrl(), registry.get(SignerRole.RELAYER).address());
            assertNotNull(ctx.getBean(TransactionReconcileService.class));
            assertNotNull(ctx.getBean(TxReconcileScheduler.class));
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'avalanche_gateway'"
                    + " AND table_name IN ('avalanche_tx','avalanche_nonce')", Integer.class), "Flyway V1 created the schema");

            TransactionSubmissionService submission = ctx.getBean(TransactionSubmissionService.class);
            TxRecord submitted = submission.submit(new SubmitRequest("op-wiring", SignerRole.RELAYER,
                    stack.usdc(), EscrowCalls.erc20Mint(registry.get(SignerRole.RELAYER).address(),
                    BigInteger.ONE), 100_000));
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
