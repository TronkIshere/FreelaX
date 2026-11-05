package com.freelax.avalanchegateway.tx;

import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.chain.ChainClients;
import com.freelax.avalanchegateway.chain.LogEntry;
import com.freelax.avalanchegateway.dto.request.SubmitRequest;
import com.freelax.avalanchegateway.entity.ScannedEvent;
import com.freelax.avalanchegateway.entity.TxRecord;
import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.exception.ChainClientException;
import com.freelax.avalanchegateway.repository.ScanRepository;
import com.freelax.avalanchegateway.scan.EventDecoder;
import com.freelax.avalanchegateway.scan.LogScanner;
import com.freelax.avalanchegateway.scan.ProviderProfile;
import com.freelax.avalanchegateway.scan.ScanCoordinator;
import com.freelax.avalanchegateway.scan.WsHintListener;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.signer.SignerRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.web3j.crypto.Hash;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M4: bounded restart-safe log scanning on a real Anvil and MySQL. The scanned contract is a hand-assembled runtime that
 * emits one known log per call, so no Solidity toolchain is involved; every test uses its own emitter address.
 */
@Testcontainers(disabledWithoutDocker = true)
class ScanTest {

    private static final String TOPIC = Hash.sha3String("FreelaXProbe()");
    private static final String EMITTER_CODE = "0x7f" + TOPIC.substring(2) + "60006000a100";
    private static final AtomicInteger NEXT = new AtomicInteger(0xE0000);

    private final List<Harness> harnesses = new ArrayList<>();
    private final List<FaultProxy> proxies = new ArrayList<>();
    private final List<AutoCloseable> closeables = new ArrayList<>();

    @BeforeAll
    static void start() {
        LocalStack.start();
    }

    @BeforeEach
    void clean() {
        JdbcTemplate jdbc = new JdbcTemplate(LocalStack.dataSource());
        jdbc.update("DELETE FROM avalanche_event");
        jdbc.update("DELETE FROM avalanche_scan_cursor");
        jdbc.update("DELETE FROM avalanche_tx");
        jdbc.update("DELETE FROM avalanche_nonce");
        AnvilRpc.setAutomine(LocalStack.anvilUrl(), true);
    }

    @AfterEach
    void closeAll() throws Exception {
        for (AutoCloseable c : closeables) {
            c.close();
        }
        harnesses.forEach(Harness::close);
        proxies.forEach(FaultProxy::close);
    }

    private static String newEmitter() {
        String address = String.format("0x%040x", NEXT.incrementAndGet());
        AnvilRpc.setCode(LocalStack.anvilUrl(), address, EMITTER_CODE);
        return address;
    }

    private Harness funded() {
        SignerRegistry registry = Harness.newRegistry();
        AnvilRpc.fund(LocalStack.anvilUrl(), registry.get(SignerRole.RELAYER).address());
        Harness h = new Harness(LocalStack.dataSource(), LocalStack.anvilUrl(), 5000, Harness.props(30_000, 2), registry);
        harnesses.add(h);
        return h;
    }

    private void emit(Harness h, String emitter, String keyPrefix, int count) {
        TxRecord last = null;
        for (int i = 0; i < count; i++) {
            last = h.submission.submit(new SubmitRequest(keyPrefix + i, SignerRole.RELAYER, emitter, "0x", 100_000));
        }
        for (int i = 0; i < 100 && AnvilRpc.transactionCount(LocalStack.anvilUrl(), last.signerAddress(), "latest") <= last.nonce(); i++) {
            sleep(100);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void await(BooleanSupplier condition, String message) {
        for (int i = 0; i < 150; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep(100);
        }
        throw new AssertionError("timed out: " + message);
    }

    private static EventDecoder decoder() {
        return new EventDecoder().register(TOPIC, "Probe");
    }

    private static ScanRepository repository() {
        JdbcTemplate jdbc = new JdbcTemplate(LocalStack.dataSource());
        return new ScanRepository(jdbc, new TransactionTemplate(
                new DataSourceTransactionManager(LocalStack.dataSource())), Clock.systemUTC());
    }

    private ChainClient client(String url) {
        ChainClient client = ChainClients.create(AvalancheEnvironment.LOCAL_SIMULATION, url, 5000, 0);
        closeables.add(client::shutdown);
        return client;
    }

    private static LogScanner scanner(ChainClient client, String emitter, long startBlock, long rangeOverride, ProviderProfile profile) {
        return new LogScanner(client, Harness.LOCAL, repository(), decoder(), emitter, startBlock, 1, profile, rangeOverride);
    }

    private static Set<String> keys(List<ScannedEvent> events) {
        Set<String> keys = new HashSet<>();
        events.forEach(e -> keys.add(e.txHash() + "#" + e.logIndex()));
        return keys;
    }

    // ---- scanning and the safe head ------------------------------------------------------------------------------------

    @Test
    void scanFindsEventsStopsAtTheSafeHeadAndIsIdempotent() {
        Harness h = funded();
        String emitter = newEmitter();
        long startBlock = AnvilRpc.blockNumber(LocalStack.anvilUrl());
        emit(h, emitter, "scan-a-", 5);
        LogScanner scanner = scanner(client(LocalStack.anvilUrl()), emitter, startBlock, 0, ProviderProfile.LOCAL);

        LogScanner.ScanResult first = scanner.scanOnce();
        assertEquals(4, first.newEvents(), "the newest block is inside the confirmation window and is not scanned yet");
        assertEquals(AnvilRpc.blockNumber(LocalStack.anvilUrl()) - 1, first.cursor());

        AnvilRpc.mine(LocalStack.anvilUrl());
        assertEquals(1, scanner.scanOnce().newEvents());
        assertEquals(0, scanner.scanOnce().newEvents(), "a repeated pass finds nothing new");
        List<ScannedEvent> events = repository().events("LOCAL_SIMULATION", 31337, emitter);
        assertEquals(5, events.size());
        assertTrue(events.stream().allMatch(e -> "Probe".equals(e.eventName()) && e.topic0().equals(TOPIC)));
    }

    @Test
    void aCrashInTheMiddleOfAScanResumesFromTheCursorAndEqualsASinglePass() throws Exception {
        Harness h = funded();
        String emitter = newEmitter();
        long startBlock = AnvilRpc.blockNumber(LocalStack.anvilUrl());
        emit(h, emitter, "scan-b-", 12);
        AnvilRpc.mine(LocalStack.anvilUrl());
        AnvilRpc.mine(LocalStack.anvilUrl());

        FaultProxy proxy = new FaultProxy(LocalStack.anvilUrl());
        proxies.add(proxy);
        proxy.failLogsAfter = 2;
        LogScanner crashing = scanner(client(proxy.url()), emitter, startBlock, 3, ProviderProfile.LOCAL);
        assertThrows(ChainClientException.class, crashing::scanOnce);
        long cursorAfterCrash = repository().cursor("LOCAL_SIMULATION", 31337, emitter).orElseThrow();
        int partial = repository().events("LOCAL_SIMULATION", 31337, emitter).size();
        assertTrue(cursorAfterCrash >= startBlock && partial < 12, "the crash left a partial but consistent state");

        ChainClient direct = client(LocalStack.anvilUrl());
        LogScanner restarted = scanner(direct, emitter, startBlock, 3, ProviderProfile.LOCAL);
        restarted.scanOnce();
        long safeHead = direct.latestBlockNumber() - 1;
        Set<String> independent = new HashSet<>();
        direct.logs(emitter, startBlock + 1, safeHead).forEach(l -> independent.add(l.txHash().toLowerCase() + "#" + l.logIndex()));
        assertEquals(12, independent.size());
        assertEquals(independent, keys(repository().events("LOCAL_SIMULATION", 31337, emitter)),
                "crash and restart equals one uninterrupted pass and an independent read");
        assertEquals(0, restarted.scanOnce().newEvents());
    }

    // ---- dedupe, cursor monotonicity, filtering, isolation -----------------------------------------------------------

    private static ScannedEvent event(String env, long chain, String contract, String tx, int index, long block) {
        return new ScannedEvent(env, chain, contract, tx, index, block, "0x" + "bb".repeat(32), TOPIC, List.of(TOPIC), "0x", "Probe");
    }

    @Test
    void aReplayedChunkIsSuppressedAndTheCursorNeverMovesBackwards() {
        ScanRepository repo = repository();
        String contract = newEmitter();
        List<ScannedEvent> chunk = List.of(event("LOCAL_SIMULATION", 31337, contract, "0x" + "01".repeat(32), 0, 10),
                event("LOCAL_SIMULATION", 31337, contract, "0x" + "02".repeat(32), 3, 11));
        assertEquals(2, repo.saveChunk("LOCAL_SIMULATION", 31337, contract, chunk, 100));
        assertEquals(0, repo.saveChunk("LOCAL_SIMULATION", 31337, contract, chunk, 100), "a duplicate chunk adds nothing");
        repo.saveChunk("LOCAL_SIMULATION", 31337, contract, List.of(), 50);
        assertEquals(100L, repo.cursor("LOCAL_SIMULATION", 31337, contract).orElseThrow());
        assertEquals(2, repo.events("LOCAL_SIMULATION", 31337, contract).size());
    }

    @Test
    void onlyLogsOfTheConfiguredContractWithAKnownTopicInsideTheRangeAreAccepted() {
        String contract = newEmitter();
        ChainClient mock = Mockito.mock(ChainClient.class);
        Mockito.when(mock.latestBlockNumber()).thenReturn(20L);
        List<String> known = List.of(TOPIC);
        List<LogEntry> returned = List.of(
                new LogEntry("0x" + "a1".repeat(32), 0, 5, "0x" + "cc".repeat(32), contract, known, "0x", false),
                new LogEntry("0x" + "a2".repeat(32), 0, 6, "0x" + "cc".repeat(32), "0x" + "99".repeat(20), known, "0x", false),
                new LogEntry("0x" + "a3".repeat(32), 0, 25, "0x" + "cc".repeat(32), contract, known, "0x", false),
                new LogEntry("0x" + "a4".repeat(32), 0, 7, "0x" + "cc".repeat(32), contract, List.of("0x" + "dd".repeat(32)), "0x", false),
                new LogEntry("0x" + "a5".repeat(32), 0, 8, "0x" + "cc".repeat(32), contract, known, "0x", true),
                new LogEntry("0x" + "a6".repeat(32), 0, 9, "0x" + "cc".repeat(32), contract, List.of(), "0x", false));
        Mockito.when(mock.logs(Mockito.eq(contract.toLowerCase()), Mockito.eq(1L), Mockito.eq(19L))).thenReturn(returned);

        LogScanner.ScanResult result = scanner(mock, contract, 1, 0, ProviderProfile.LOCAL).scanOnce();
        assertEquals(6, result.fetchedLogs());
        assertEquals(1, result.acceptedLogs(), "foreign address, out-of-range block, unknown topic, removed and topic-less logs are ignored");
        assertEquals(1, repository().events("LOCAL_SIMULATION", 31337, contract).size());
    }

    @Test
    void anotherEnvironmentsCursorAndEventsAreNeverTouched() {
        Harness h = funded();
        String emitter = newEmitter();
        JdbcTemplate jdbc = new JdbcTemplate(LocalStack.dataSource());
        jdbc.update("INSERT INTO avalanche_scan_cursor VALUES ('AVALANCHE_FUJI', 43113, ?, 777, 1)", emitter.toLowerCase());
        long startBlock = AnvilRpc.blockNumber(LocalStack.anvilUrl());
        emit(h, emitter, "scan-iso-", 3);
        AnvilRpc.mine(LocalStack.anvilUrl());
        scanner(client(LocalStack.anvilUrl()), emitter, startBlock, 0, ProviderProfile.LOCAL).scanOnce();

        assertEquals(777L, jdbc.queryForObject("SELECT last_scanned_block FROM avalanche_scan_cursor WHERE environment='AVALANCHE_FUJI'", Long.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM avalanche_event WHERE environment='AVALANCHE_FUJI'", Integer.class));
        assertEquals(3, repository().events("LOCAL_SIMULATION", 31337, emitter).size());
        assertTrue(repository().events("AVALANCHE_FUJI", 43113, emitter).isEmpty());
    }

    // ---- provider behaviour --------------------------------------------------------------------------------------------

    @Test
    void theRangeIsHalvedWhenTheProviderRefusesItAndNothingIsMissed() throws Exception {
        Harness h = funded();
        String emitter = newEmitter();
        long startBlock = AnvilRpc.blockNumber(LocalStack.anvilUrl());
        emit(h, emitter, "scan-r-", 6);
        for (int i = 0; i < 40; i++) {
            AnvilRpc.mine(LocalStack.anvilUrl());
        }
        FaultProxy proxy = new FaultProxy(LocalStack.anvilUrl());
        proxies.add(proxy);
        proxy.maxLogRange = 10;
        LogScanner scanner = scanner(client(proxy.url()), emitter, startBlock, 0, new ProviderProfile(100, true, 0));

        LogScanner.ScanResult result = scanner.scanOnce();
        assertEquals(6, repository().events("LOCAL_SIMULATION", 31337, emitter).size());
        assertEquals(6, scanner.currentRange(), "100 -> 50 -> 25 -> 12 -> 6");
        assertTrue(proxy.logRanges.size() > result.chunks(), "some requests were refused by the provider and retried smaller");
        assertTrue(proxy.logRanges.stream().skip(proxy.logRanges.size() - result.chunks()).allMatch(r -> r[1] - r[0] + 1 <= 10),
                "the accepted requests respect the provider cap");
        assertTrue(result.chunks() > 1);
    }

    @Test
    void retryAfterMakesTheFollowingCallsWaitAndThrottlingIsNeverEvidence() throws Exception {
        FaultProxy proxy = new FaultProxy(LocalStack.anvilUrl());
        proxies.add(proxy);
        proxy.retryAfterSeconds = 1;
        proxy.rateLimited = true;
        ChainClient client = client(proxy.url());
        assertThrows(ChainClientException.class, client::latestBlockNumber);
        proxy.reset();
        long start = System.nanoTime();
        client.latestBlockNumber();
        long waitedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(waitedMs >= 800, "the next call waited for Retry-After, waited " + waitedMs + " ms");
    }

    @Test
    void providerProfilesFollowTheMeasuredLimitsAndNeverAssume2000OnAPublicHost() {
        assertEquals(100, ProviderProfile.forHost("api.avax-test.network", false).maxLogRange());
        assertEquals(100, ProviderProfile.forHost("unknown.example", false).maxLogRange());
        assertEquals(50_000, ProviderProfile.forHost("avalanche-fuji-c-chain-rpc.publicnode.com", false).maxLogRange());
        assertTrue(ProviderProfile.forHost("avalanche-fuji-c-chain-rpc.publicnode.com", false).requireAddress());
        assertEquals(2000, ProviderProfile.forHost("localhost", true).maxLogRange());
        assertEquals(100, scanner(Mockito.mock(ChainClient.class), newEmitter(), 0, 5000, ProviderProfile.CONSERVATIVE).currentRange(),
                "an override can only lower the profile limit");
    }

    // ---- WebSocket hints, duplicates, missed hints, periodic pass ---------------------------------------------------

    private static String wsUrl() {
        return LocalStack.anvilUrl().replaceFirst("^http://", "ws://");
    }

    @Test
    void hintsTriggerScansDuplicateSubscriptionsDoNotDuplicateEventsAndMissedHintsAreRecoveredByTheCursor() {
        Harness h = funded();
        String emitter = newEmitter();
        long startBlock = AnvilRpc.blockNumber(LocalStack.anvilUrl());
        ScanCoordinator coordinator = new ScanCoordinator(scanner(client(LocalStack.anvilUrl()), emitter, startBlock, 0, ProviderProfile.LOCAL), 0);
        closeables.add(coordinator);
        ScanRepository repo = repository();

        // 1. missed hints: events happen with no listener at all
        emit(h, emitter, "scan-ws-miss-", 3);
        AnvilRpc.mine(LocalStack.anvilUrl());
        assertEquals(0, repo.events("LOCAL_SIMULATION", 31337, emitter).size());

        // 2. the (re)connect asks for a pass: the cursor recovers the gap with bounded reads
        WsHintListener first = new WsHintListener(wsUrl(), coordinator);
        closeables.add(first);
        first.start();
        await(() -> first.connections() >= 1 && repo.events("LOCAL_SIMULATION", 31337, emitter).size() == 3, "gap recovered after connect");

        // 3. a live hint triggers a scan sooner (no periodic timer is running)
        long hintsBefore = first.hints();
        emit(h, emitter, "scan-ws-live-", 2);
        AnvilRpc.mine(LocalStack.anvilUrl());
        await(() -> first.hints() > hintsBefore && repo.events("LOCAL_SIMULATION", 31337, emitter).size() == 5, "live hint triggered a scan");

        // 4. an overlapping second subscription duplicates hints, never events
        WsHintListener second = new WsHintListener(wsUrl(), coordinator);
        closeables.add(second);
        second.start();
        await(() -> second.connections() >= 1, "second subscription connected");
        long firstBefore = first.hints();
        long secondBefore = second.hints();
        emit(h, emitter, "scan-ws-dup-", 2);
        AnvilRpc.mine(LocalStack.anvilUrl());
        await(() -> first.hints() > firstBefore && second.hints() > secondBefore && repo.events("LOCAL_SIMULATION", 31337, emitter).size() == 7,
                "both subscriptions hinted");
        assertEquals(7, repo.events("LOCAL_SIMULATION", 31337, emitter).size());
        assertEquals(7, keys(repo.events("LOCAL_SIMULATION", 31337, emitter)).size(), "no duplicate (tx hash, log index)");
    }

    @Test
    void thePeriodicPassGuaranteesProgressWhenNoHintEverArrives() {
        Harness h = funded();
        String emitter = newEmitter();
        long startBlock = AnvilRpc.blockNumber(LocalStack.anvilUrl());
        ScanCoordinator coordinator = new ScanCoordinator(scanner(client(LocalStack.anvilUrl()), emitter, startBlock, 0, ProviderProfile.LOCAL), 200);
        closeables.add(coordinator);
        coordinator.start();
        emit(h, emitter, "scan-periodic-", 3);
        AnvilRpc.mine(LocalStack.anvilUrl());
        await(() -> repository().events("LOCAL_SIMULATION", 31337, emitter).size() == 3, "periodic pass");
        assertFalse(coordinator.passes() == 0);
    }
}
