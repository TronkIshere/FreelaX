package com.freelax.avalanchegateway.tx;

import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.chain.ChainClients;
import com.freelax.avalanchegateway.contract.DeploymentManifest;
import com.freelax.avalanchegateway.contract.EscrowCalls;
import com.freelax.avalanchegateway.contract.ManifestVerifier;
import com.freelax.avalanchegateway.contract.VerifiedDeployment;
import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.env.EnvironmentGuardException;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.signer.SignerRole;
import com.freelax.avalanchegateway.tx.SubmissionService.SubmitRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The manifest against live chains: real deployments, the manifest produced by the real forge script, and the submission allow-list. */
@Testcontainers(disabledWithoutDocker = true)
class ManifestIntegrationTest {

    private final List<Harness> harnesses = new ArrayList<>();
    private final List<ChainClient> clients = new ArrayList<>();

    @BeforeAll
    static void start() {
        LocalStack.start();
    }

    @AfterEach
    void close() {
        harnesses.forEach(Harness::close);
        clients.forEach(ChainClient::shutdown);
    }

    private ChainClient client(String url) {
        ChainClient c = ChainClients.create(AvalancheEnvironment.LOCAL_SIMULATION, url, 5000, 0);
        clients.add(c);
        return c;
    }

    @Test
    void aRealDeploymentVerifiesAndAWrongOneIsRefused(@TempDir Path dir) throws Exception {
        String url = LocalStack.anvilUrl();
        ContractFixtures.Stack stack = ContractFixtures.deployStack(url, ContractFixtures.ANVIL_ACCOUNT_0);
        ContractFixtures.Stack other = ContractFixtures.deployStack(url, ContractFixtures.ANVIL_ACCOUNT_0);
        ChainClient chain = client(url);

        Path ok = ContractFixtures.writeManifest(dir.resolve("ok.json"), "LOCAL_SIMULATION", 31337, stack, 0);
        VerifiedDeployment verified = ManifestVerifier.verify(DeploymentManifest.read(ok), Harness.LOCAL, chain);
        assertEquals(Set.of(stack.escrow(), stack.usdc()), verified.allowedTargets());

        ContractFixtures.Stack mismatched = new ContractFixtures.Stack(other.usdc(), stack.escrow());
        Path wrongToken = ContractFixtures.writeManifest(dir.resolve("wrong-token.json"), "LOCAL_SIMULATION", 31337, mismatched, 0);
        assertEquals("MANIFEST_TOKEN_MISMATCH", assertThrows(EnvironmentGuardException.class,
                () -> ManifestVerifier.verify(DeploymentManifest.read(wrongToken), Harness.LOCAL, chain)).code());

        ContractFixtures.Stack noCode = new ContractFixtures.Stack(stack.usdc(), "0x" + "77".repeat(20));
        Path missing = ContractFixtures.writeManifest(dir.resolve("missing.json"), "LOCAL_SIMULATION", 31337, noCode, 0);
        assertEquals("MANIFEST_CODE_MISSING", assertThrows(EnvironmentGuardException.class,
                () -> ManifestVerifier.verify(DeploymentManifest.read(missing), Harness.LOCAL, chain)).code());

        Path wrongChain = ContractFixtures.writeManifest(dir.resolve("wrong-chain.json"), "LOCAL_SIMULATION", 43113, stack, 0);
        assertEquals("MANIFEST_CHAIN_MISMATCH", assertThrows(EnvironmentGuardException.class,
                () -> ManifestVerifier.verify(DeploymentManifest.read(wrongChain), Harness.LOCAL, chain)).code());
    }

    @Test
    void submissionIsRestrictedToTheManifestContracts(@TempDir Path dir) throws Exception {
        String url = LocalStack.anvilUrl();
        ContractFixtures.Stack stack = ContractFixtures.deployStack(url, ContractFixtures.ANVIL_ACCOUNT_0);
        SignerRegistry registry = Harness.newRegistry();
        AnvilRpc.fund(url, registry.get(SignerRole.RELAYER).address());
        new JdbcTemplate(LocalStack.dataSource()).update("DELETE FROM avalanche_tx");
        new JdbcTemplate(LocalStack.dataSource()).update("DELETE FROM avalanche_nonce");
        Set<String> allowed = Set.of(stack.escrow(), stack.usdc());
        Harness h = new Harness(LocalStack.dataSource(), url, 5000, Harness.props(30_000, 2), registry, null, allowed);
        harnesses.add(h);

        String mint = EscrowCalls.erc20Mint(registry.get(SignerRole.RELAYER).address(), BigInteger.ONE);
        TxRecord ok = h.submission.submit(new SubmitRequest("allow-ok", SignerRole.RELAYER, stack.usdc().toUpperCase(Locale.ROOT).replace("0X", "0x"), mint, 100_000));
        assertEquals(TxState.SUBMITTED, ok.state());
        SubmissionService.InvalidRequestException refused = assertThrows(SubmissionService.InvalidRequestException.class,
                () -> h.submission.submit(new SubmitRequest("allow-bad", SignerRole.RELAYER, "0x" + "99".repeat(20), mint, 100_000)));
        assertTrue(refused.getMessage().startsWith("TARGET_NOT_ALLOWED"), refused.getMessage());
        assertTrue(h.repository.find("LOCAL_SIMULATION", 31337, "allow-bad").isEmpty(), "a refused target creates no record");
        assertEquals(ok.nonce() + 1, h.allocator.peekNext("LOCAL_SIMULATION", 31337, ok.signerAddress()), "and consumes no nonce");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AVALANCHE_TEST_MANIFEST", matches = ".+")
    void theManifestProducedByTheRealForgeScriptVerifiesAgainstItsAnvil() {
        String url = System.getenv().getOrDefault("AVALANCHE_TEST_ANVIL_URL", "http://127.0.0.1:8545");
        ChainClient chain = client(url);
        DeploymentManifest manifest = DeploymentManifest.read(Path.of(System.getenv("AVALANCHE_TEST_MANIFEST")));
        VerifiedDeployment verified = ManifestVerifier.verify(manifest, Harness.LOCAL, chain);
        assertEquals("MOCK", verified.manifest().tokenKind());
        assertEquals(2, verified.allowedTargets().size());
    }
}
