package com.freelax.avalanchegateway.tx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.chain.ChainClients;
import com.freelax.avalanchegateway.contract.EscrowAuthorizations;
import com.freelax.avalanchegateway.contract.EscrowCalls;
import com.freelax.avalanchegateway.contract.EscrowErrors;
import com.freelax.avalanchegateway.contract.EscrowEvents;
import com.freelax.avalanchegateway.contract.EscrowReader;
import com.freelax.avalanchegateway.contract.EscrowReconciler;
import com.freelax.avalanchegateway.contract.EscrowState;
import com.freelax.avalanchegateway.contract.FundTerms;
import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.scan.EventDecoder;
import com.freelax.avalanchegateway.scan.LogScanner;
import com.freelax.avalanchegateway.scan.ProviderProfile;
import com.freelax.avalanchegateway.scan.ScanRepository;
import com.freelax.avalanchegateway.scan.ScannedEvent;
import com.freelax.avalanchegateway.signer.Signature;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.signer.SignerRole;
import com.freelax.avalanchegateway.tx.SubmissionService.SubmitRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real compiled FreelaXEscrow and MockUSDC on a local Anvil, driven by the gateway: Java and Solidity must agree on every
 * EIP-712 digest, the contract must accept Java-built authorizations and refuse mis-bound ones, events and revert reasons must
 * decode, and scanned events must reconcile with the contract's own state.
 */
@Testcontainers(disabledWithoutDocker = true)
class EscrowContractTest {

    private static final String ANVIL_ACCOUNT_0 = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static SignerRegistry registry;
    private static String usdc;
    private static String escrowAddress;
    private static Harness harness;
    private static ChainClient client;
    private static long startBlock;
    private static int sequence;

    @BeforeAll
    static void deploy() throws Exception {
        LocalStack.start();
        String url = LocalStack.anvilUrl();
        new JdbcTemplate(LocalStack.dataSource()).update("DELETE FROM avalanche_event");
        new JdbcTemplate(LocalStack.dataSource()).update("DELETE FROM avalanche_scan_cursor");
        new JdbcTemplate(LocalStack.dataSource()).update("DELETE FROM avalanche_tx");
        new JdbcTemplate(LocalStack.dataSource()).update("DELETE FROM avalanche_nonce");
        registry = Harness.newRegistry();
        for (SignerRole role : SignerRole.values()) {
            AnvilRpc.fund(url, registry.get(role).address());
        }
        startBlock = AnvilRpc.blockNumber(url);
        usdc = deploy(url, resource("MockUSDC.bin"));
        String constructorArgs = FunctionEncoder.encodeConstructor(List.of(new Address(ANVIL_ACCOUNT_0), new Address(usdc),
                new Address(registry.get(SignerRole.ARBITER).address())));
        escrowAddress = deploy(url, resource("FreelaXEscrow.bin") + constructorArgs);
        sendFromAdmin(url, escrowAddress, FunctionEncoder.encode(new Function("grantRole", List.of(
                new Bytes32(Hash.sha3String("AUTH_SIGNER_ROLE") == null ? new byte[32] : Numeric.hexStringToByteArray(Hash.sha3String("AUTH_SIGNER_ROLE"))),
                new Address(registry.get(SignerRole.AUTHORIZATION).address())), List.of())));
        harness = new Harness(LocalStack.dataSource(), url, 5000, Harness.props(30_000, 2), registry);
        client = harness.client;
    }

    @AfterAll
    static void close() {
        if (harness != null) {
            harness.close();
        }
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

    private static String resource(String name) throws IOException {
        try (var in = EscrowContractTest.class.getResourceAsStream("/contracts/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    private static String sendFromAdmin(String url, String to, String data) {
        String params = "{\"from\":\"" + ANVIL_ACCOUNT_0 + "\"" + (to == null ? "" : ",\"to\":\"" + to + "\"") + ",\"data\":\"" + data + "\",\"gas\":\"0x1c9c380\"}";
        String body = AnvilRpc.call(url, "eth_sendTransaction", params);
        Matcher m = Pattern.compile("\"result\":\"(0x[0-9a-fA-F]{64})\"").matcher(body);
        assertTrue(m.find(), body);
        return m.group(1);
    }

    private static String deploy(String url, String creationCode) {
        String hash = sendFromAdmin(url, null, creationCode);
        for (int i = 0; i < 60; i++) {
            Matcher m = Pattern.compile("\"contractAddress\":\"(0x[0-9a-fA-F]{40})\"").matcher(AnvilRpc.call(url, "eth_getTransactionReceipt", "\"" + hash + "\""));
            if (m.find()) {
                return m.group(1).toLowerCase(Locale.ROOT);
            }
            sleep(100);
        }
        throw new AssertionError("deployment not mined");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String addr(SignerRole role) {
        return registry.get(role).address().toLowerCase(Locale.ROOT);
    }

    private static String id(String label) {
        return Hash.sha3String(label + "-" + (++sequence) + "-" + System.nanoTime());
    }

    private static long now() {
        return AnvilRpc.blockTimestamp(LocalStack.anvilUrl());
    }

    private static byte[] bytes(Signature s) {
        return Numeric.hexStringToByteArray(s.toHex());
    }

    private static FundTerms terms(String escrowId, long amount) {
        long t = now();
        return new FundTerms(escrowId, addr(SignerRole.TEST_BUYER), addr(SignerRole.TEST_FREELANCER), usdc, BigInteger.valueOf(amount),
                t + 3600, t + 7 * 86400, 72, 2, false, addr(SignerRole.ARBITER));
    }

    private static String callDigest(String data) {
        String body = AnvilRpc.call(LocalStack.anvilUrl(), "eth_call", "{\"to\":\"" + escrowAddress + "\",\"data\":\"" + data + "\"}", "\"latest\"");
        Matcher m = Pattern.compile("\"result\":\"(0x[0-9a-fA-F]{64})\"").matcher(body);
        assertTrue(m.find(), body);
        return m.group(1);
    }

    private TxRecord send(SignerRole role, String to, String data, long gas) {
        TxRecord submitted = harness.submission.submit(new SubmitRequest("contract-" + (++sequence) + "-" + System.nanoTime(), role, to, data, gas));
        TxRecord record = submitted;
        for (int i = 0; i < 80 && record.state() != TxState.CONFIRMED && record.state() != TxState.FINALIZED && record.state() != TxState.REVERTED; i++) {
            sleep(100);
            record = harness.tracker.reconcile(submitted.id());
        }
        return record;
    }

    private EscrowAuthorizations auth() {
        return new EscrowAuthorizations(31337, escrowAddress);
    }

    private EscrowState state(String escrowId) {
        return new EscrowReader(client, escrowAddress).read(escrowId);
    }

    private void mintAndApprove(long amount) {
        sendFromAdmin(LocalStack.anvilUrl(), usdc, EscrowCalls.erc20Mint(addr(SignerRole.TEST_BUYER), BigInteger.valueOf(amount)));
        TxRecord approve = send(SignerRole.TEST_BUYER, usdc, EscrowCalls.erc20Approve(escrowAddress, BigInteger.valueOf(amount)), 100_000);
        assertEquals(TxState.CONFIRMED, approve.state());
    }

    private String fundEscrow(String escrowId, long amount) {
        mintAndApprove(amount);
        FundTerms t = terms(escrowId, amount);
        long deadline = now() + 3600;
        Signature sig = EscrowAuthorizations.sign(registry.get(SignerRole.AUTHORIZATION), auth().fundTypedData(t, 0, deadline));
        TxRecord funded = send(SignerRole.TEST_BUYER, escrowAddress, EscrowCalls.fund(t, deadline, bytes(sig)), 1_000_000);
        assertEquals(TxState.CONFIRMED, funded.state(), "fund must mine with status 1");
        return escrowId;
    }

    private void submitWork(String escrowId, String evidence) {
        EscrowState s = state(escrowId);
        long deadline = now() + 3600;
        Signature sig = EscrowAuthorizations.sign(registry.get(SignerRole.AUTHORIZATION), auth().submitTypedData(escrowId, evidence, s.submissionCount(), s.authNonce(), deadline));
        assertEquals(TxState.CONFIRMED, send(SignerRole.TEST_FREELANCER, escrowAddress, EscrowCalls.submit(escrowId, evidence, deadline, bytes(sig)), 500_000).state());
    }

    // ---- digest parity -----------------------------------------------------------------------------------------------

    @Test
    void javaAndSolidityComputeTheSameDigestForEveryAuthorization() {
        String escrowId = id("parity");
        FundTerms t = terms(escrowId, 12_345_678);
        long deadline = now() + 3600;
        assertEquals(callDigest(EscrowCalls.fundDigest(t, 0, deadline)), Numeric.toHexString(EscrowAuthorizations.digest(auth().fundTypedData(t, 0, deadline))));

        String evidence = Hash.sha3String("evidence");
        assertEquals(callDigest(EscrowCalls.submitDigest(escrowId, evidence, 3, 7, deadline)),
                Numeric.toHexString(EscrowAuthorizations.digest(auth().submitTypedData(escrowId, evidence, 3, 7, deadline))));
        String feedback = Hash.sha3String("feedback");
        assertEquals(callDigest(EscrowCalls.revisionDigest(escrowId, feedback, 1, 9, deadline)),
                Numeric.toHexString(EscrowAuthorizations.digest(auth().revisionTypedData(escrowId, feedback, 1, 9, deadline))));
        assertEquals(callDigest(EscrowCalls.mutualRefundDigest(escrowId, 4, deadline)),
                Numeric.toHexString(EscrowAuthorizations.digest(auth().mutualRefundTypedData(escrowId, 4, deadline))));
        String settlement = Hash.sha3String("settlement");
        for (boolean toFreelancer : new boolean[]{true, false}) {
            assertEquals(callDigest(EscrowCalls.mutualSettlementDigest(escrowId, toFreelancer, BigInteger.valueOf(12_345_678), settlement, 1_700_000_123L, 5, deadline)),
                    Numeric.toHexString(EscrowAuthorizations.digest(auth().mutualSettlementTypedData(escrowId, toFreelancer, BigInteger.valueOf(12_345_678),
                            settlement, 1_700_000_123L, 5, deadline))), "R12 settlement digest parity, releaseToFreelancer=" + toFreelancer);
        }
    }

    @Test
    void aDigestForAnotherChainOrAnotherContractDiffersAndTheContractRefusesTheSignature() {
        String escrowId = id("binding");
        mintAndApprove(50_000_000);
        FundTerms t = terms(escrowId, 50_000_000);
        long deadline = now() + 3600;
        String right = auth().fundTypedData(t, 0, deadline);
        String otherChain = new EscrowAuthorizations(31338, escrowAddress).fundTypedData(t, 0, deadline);
        String otherContract = new EscrowAuthorizations(31337, "0x" + "ab".repeat(20)).fundTypedData(t, 0, deadline);
        assertNotEquals(Numeric.toHexString(EscrowAuthorizations.digest(right)), Numeric.toHexString(EscrowAuthorizations.digest(otherChain)));
        assertNotEquals(Numeric.toHexString(EscrowAuthorizations.digest(right)), Numeric.toHexString(EscrowAuthorizations.digest(otherContract)));

        for (String wrong : new String[]{otherChain, otherContract}) {
            Signature sig = EscrowAuthorizations.sign(registry.get(SignerRole.AUTHORIZATION), wrong);
            ChainClient.CallResult result = client.call(escrowAddress, EscrowCalls.fund(t, deadline, bytes(sig)));
            // eth_call has no sender, so check the contract's own verdict through a mined transaction instead
            TxRecord record = send(SignerRole.TEST_BUYER, escrowAddress, EscrowCalls.fund(t, deadline, bytes(sig)), 1_000_000);
            assertEquals(TxState.REVERTED, record.state(), "a mis-bound authorization must not fund");
            ChainClient.CallResult replay = client.call(registry.get(SignerRole.TEST_BUYER).address(), escrowAddress, EscrowCalls.fund(t, deadline, bytes(sig)));
            assertTrue(replay.reverted());
            assertEquals("InvalidAuthorization", EscrowErrors.decode(replay.data()).orElse("unknown"));
        }
        assertEquals(0, state(escrowId).status(), "nothing was created");
    }

    // ---- ABI parity --------------------------------------------------------------------------------------------------

    @Test
    void theAbiAgreesWithTheJavaEventSignaturesAndEveryErrorSelectorIsKnown() throws Exception {
        JsonNode abi;
        try (var in = getClass().getResourceAsStream("/abi/FreelaXEscrow.abi.json")) {
            abi = JSON.readTree(in);
        }
        Set<String> abiEvents = new HashSet<>();
        Set<String> unknownErrors = new HashSet<>();
        for (JsonNode entry : abi) {
            String name = entry.path("name").asText();
            List<String> types = new ArrayList<>();
            entry.path("inputs").forEach(input -> types.add(input.path("type").asText()));
            String signature = name + "(" + String.join(",", types) + ")";
            if (entry.path("type").asText().equals("event") && EscrowEvents.SIGNATURES.stream().anyMatch(s -> EscrowEvents.nameOf(s).equals(name))) {
                abiEvents.add(signature);
            }
            if (entry.path("type").asText().equals("error")) {
                String selector = Numeric.toHexString(Hash.sha3(signature.getBytes(StandardCharsets.UTF_8)), 0, 4, true);
                if (EscrowErrors.decode(selector + "0".repeat(8)).isEmpty()) {
                    unknownErrors.add(signature);
                }
            }
        }
        assertEquals(new HashSet<>(EscrowEvents.SIGNATURES), abiEvents, "Java event signatures equal the ABI's");
        Set<String> tolerated = Set.of("InvalidShortString()", "StringTooLong(string)");
        unknownErrors.removeAll(tolerated);
        assertTrue(unknownErrors.isEmpty(), "ABI errors the gateway cannot name: " + unknownErrors);
    }

    // ---- the lifecycle through the gateway --------------------------------------------------------------------------

    @Test
    void fullLifecycleThroughTheGatewayEventsRevertMappingAndReconciliation() throws Exception {
        String url = LocalStack.anvilUrl();
        long headBefore = AnvilRpc.blockNumber(url);

        // 1. happy path with a revision, a too-early release by a relayer, then the client's release
        String one = fundEscrow(id("happy"), 100_000_000);
        EscrowState funded = state(one);
        assertEquals("Funded", funded.statusName());
        assertEquals(addr(SignerRole.ARBITER), funded.arbiter(), "arbiter snapshot");
        assertEquals(1, funded.authNonce());

        submitWork(one, Hash.sha3String("v1"));
        long revisionDeadline = now() + 3600;
        EscrowState submitted = state(one);
        Signature revisionSig = EscrowAuthorizations.sign(registry.get(SignerRole.AUTHORIZATION),
                auth().revisionTypedData(one, Hash.sha3String("fb1"), submitted.revisionsUsed(), submitted.authNonce(), revisionDeadline));
        assertEquals(TxState.CONFIRMED, send(SignerRole.TEST_BUYER, escrowAddress,
                EscrowCalls.requestRevision(one, Hash.sha3String("fb1"), revisionDeadline, bytes(revisionSig)), 500_000).state());
        assertEquals("Revision", state(one).statusName());
        submitWork(one, Hash.sha3String("v2"));
        assertEquals(2, state(one).submissionCount());

        TxRecord early = send(SignerRole.RELAYER, escrowAddress, EscrowCalls.release(one), 300_000);
        assertEquals(TxState.REVERTED, early.state(), "a relayer cannot release before the review deadline");
        ChainClient.CallResult replay = client.call(registry.get(SignerRole.RELAYER).address(), escrowAddress, EscrowCalls.release(one));
        assertTrue(replay.reverted());
        assertEquals("TooEarly", EscrowErrors.decode(replay.data()).orElse("unknown"), "the revert reason is mapped to the contract error name");

        assertEquals(TxState.CONFIRMED, send(SignerRole.TEST_BUYER, escrowAddress, EscrowCalls.release(one), 300_000).state());
        assertEquals("Released", state(one).statusName());

        // 2. dispute and arbiter resolution (refund)
        String two = fundEscrow(id("dispute"), 40_000_000);
        submitWork(two, Hash.sha3String("work"));
        assertEquals(TxState.CONFIRMED, send(SignerRole.TEST_FREELANCER, escrowAddress, EscrowCalls.openDispute(two, Hash.sha3String("why")), 300_000).state());
        TxRecord notArbiter = send(SignerRole.TEST_BUYER, escrowAddress, EscrowCalls.resolveDispute(two, true, Hash.sha3String("d")), 300_000);
        assertEquals(TxState.REVERTED, notArbiter.state(), "only the snapshotted arbiter resolves");
        assertEquals(TxState.CONFIRMED, send(SignerRole.ARBITER, escrowAddress, EscrowCalls.resolveDispute(two, false, Hash.sha3String("d")), 300_000).state());
        assertEquals("Refunded", state(two).statusName());

        // 3. mutual refund: both parties sign with their own development wallets, a relayer submits
        String three = fundEscrow(id("mutual"), 30_000_000);
        long refundDeadline = now() + 3600;
        long nonce = state(three).authNonce();
        String typed = auth().mutualRefundTypedData(three, nonce, refundDeadline);
        Signature buyerSig = EscrowAuthorizations.sign(registry.get(SignerRole.TEST_BUYER), typed);
        Signature freelancerSig = EscrowAuthorizations.sign(registry.get(SignerRole.TEST_FREELANCER), typed);
        assertEquals(TxState.CONFIRMED, send(SignerRole.RELAYER, escrowAddress,
                EscrowCalls.mutualRefund(three, refundDeadline, bytes(buyerSig), bytes(freelancerSig)), 300_000).state());
        assertEquals("Refunded", state(three).statusName());

        // 3b. R12 mutual settlement of a Disputed escrow: both parties sign, a relayer submits, the arbiter is not involved
        String settled = fundEscrow(id("settle"), 25_000_000);
        submitWork(settled, Hash.sha3String("work"));
        assertEquals(TxState.CONFIRMED, send(SignerRole.TEST_BUYER, escrowAddress, EscrowCalls.openDispute(settled, Hash.sha3String("late")), 300_000).state());
        EscrowState disputed = state(settled);
        long settleDeadline = now() + 3600;
        String settlementHash = Hash.sha3String("settlement-record");
        String settleTyped = auth().mutualSettlementTypedData(settled, true, disputed.amount(), settlementHash, disputed.disputedAt(), disputed.authNonce(), settleDeadline);
        Signature clientSettle = EscrowAuthorizations.sign(registry.get(SignerRole.TEST_BUYER), settleTyped);
        Signature freelancerSettle = EscrowAuthorizations.sign(registry.get(SignerRole.TEST_FREELANCER), settleTyped);
        TxRecord swapped = send(SignerRole.RELAYER, escrowAddress,
                EscrowCalls.mutualSettleDispute(settled, true, settlementHash, settleDeadline, bytes(freelancerSettle), bytes(clientSettle)), 300_000);
        assertEquals(TxState.REVERTED, swapped.state(), "signatures in swapped positions are refused");
        assertEquals(TxState.CONFIRMED, send(SignerRole.RELAYER, escrowAddress,
                EscrowCalls.mutualSettleDispute(settled, true, settlementHash, settleDeadline, bytes(clientSettle), bytes(freelancerSettle)), 300_000).state());
        assertEquals("Released", state(settled).statusName());
        assertEquals(settlementHash, state(settled).resolutionHash().toLowerCase());

        // 4. scan the events and decode them
        AnvilRpc.mine(url);
        AnvilRpc.mine(url);
        ScanRepository repo = new ScanRepository(new JdbcTemplate(LocalStack.dataSource()),
                new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(LocalStack.dataSource())),
                Clock.systemUTC());
        EventDecoder decoder = EscrowEvents.registerAll(new EventDecoder());
        ChainClient scanClient = ChainClients.create(AvalancheEnvironment.LOCAL_SIMULATION, url, 5000, 0);
        try {
            LogScanner scanner = new LogScanner(scanClient, Harness.LOCAL, repo, decoder, escrowAddress, headBefore, 1, ProviderProfile.LOCAL, 0);
            scanner.scanOnce();
            List<ScannedEvent> events = repo.events("LOCAL_SIMULATION", 31337, escrowAddress);
            List<String> names = events.stream().map(ScannedEvent::eventName).toList();
            assertTrue(names.containsAll(List.of("Funded", "WorkSubmitted", "RevisionRequested", "DisputeOpened", "DisputeResolved", "DisputeSettledByParties", "Released", "Refunded")), names.toString());
            EscrowEvents.DecodedEvent settledEvent = events.stream().filter(e -> e.eventName().equals("DisputeSettledByParties"))
                    .map(EscrowEvents::decode).findFirst().orElseThrow();
            assertEquals(settled.toLowerCase(), settledEvent.escrowId());
            assertEquals(true, settledEvent.fields().get("releasedToFreelancer"));
            assertEquals(settlementHash, settledEvent.fields().get("settlementHash"));
            EscrowEvents.DecodedEvent releasedEvent = events.stream().filter(e -> e.eventName().equals("Released") && e.topics().get(1).equalsIgnoreCase(one))
                    .map(EscrowEvents::decode).findFirst().orElseThrow();
            assertEquals(BigInteger.valueOf(100_000_000), releasedEvent.fields().get("amount"));
            assertEquals(addr(SignerRole.TEST_FREELANCER), releasedEvent.fields().get("to"));
            EscrowEvents.DecodedEvent fundedEvent = events.stream().filter(e -> e.eventName().equals("Funded") && e.topics().get(1).equalsIgnoreCase(two))
                    .map(EscrowEvents::decode).findFirst().orElseThrow();
            assertEquals(addr(SignerRole.ARBITER), fundedEvent.fields().get("arbiter"));
            assertEquals(BigInteger.valueOf(40_000_000), fundedEvent.fields().get("amount"));

            // 5. reconcile: the scanned events imply the same status as the contract; tampering is detected
            EscrowReconciler reconciler = new EscrowReconciler(new EscrowReader(scanClient, escrowAddress), repo, Harness.LOCAL, escrowAddress);
            for (String escrow : List.of(one, two, three, settled)) {
                EscrowReconciler.Result r = reconciler.reconcile(escrow);
                assertTrue(r.match(), "events and contract agree for " + escrow + ": " + r);
            }
            new JdbcTemplate(LocalStack.dataSource()).update("DELETE FROM avalanche_event WHERE event_name IN ('Refunded') AND topics LIKE ?", "%" + three.substring(2) + "%");
            EscrowReconciler.Result tampered = reconciler.reconcile(three);
            assertFalse(tampered.match(), "a missing event is reported");
            assertEquals(1, tampered.expectedStatus());
            assertEquals(6, tampered.chainStatus(), "the contract wins");
        } finally {
            scanClient.shutdown();
        }
    }
}
