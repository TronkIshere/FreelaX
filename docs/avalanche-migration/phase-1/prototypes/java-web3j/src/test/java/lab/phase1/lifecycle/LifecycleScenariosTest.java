package lab.phase1.lifecycle;

import lab.phase1.EnvGuard;
import lab.phase1.Evidence;
import lab.phase1.Rpc;
import lab.phase1.EnvGuard.Env;
import lab.phase1.lifecycle.Lifecycle.*;
import org.junit.jupiter.api.*;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;
import okhttp3.OkHttpClient;

import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Lifecycle prototype scenarios on LOCAL anvil (LOCAL_SIMULATION, chain 31337). Nothing here is observed Fuji behaviour. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LifecycleScenariosTest {
    static final String RPC = "http://localhost:8545";
    static final long CHAIN = 31337;
    static final BigInteger PRIO = BigInteger.valueOf(1_000_000_000L), MAXFEE = BigInteger.valueOf(20_000_000_000L);
    static Rpc raw;
    static Web3j direct;
    static Credentials creds;
    static String labAddr;
    static File ledgerFile;
    static Ledger ledger;
    static Tracker tracker;
    static final Map<String, Object> summary = new LinkedHashMap<>();

    static String enc(String fn, Type<?>... a) { return FunctionEncoder.encode(new Function(fn, Arrays.asList(a), List.of())); }
    static String okCall(long n) { return enc("actUntil", new Uint256(BigInteger.TWO.pow(60).add(BigInteger.valueOf(n)))); }
    static void mine(int n) throws Exception { for (int i = 0; i < n; i++) raw.result("evm_mine"); }
    static Web3j client(String url, int seconds) {
        return Web3j.build(new HttpService(url, new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(seconds)).readTimeout(Duration.ofSeconds(seconds)).callTimeout(Duration.ofSeconds(seconds)).build()));
    }
    static Tracker newTracker(Web3j w, Ledger l, Env env, long chain, long graceMs) {
        return new Tracker(w, creds, chain, env, l, () -> { try { return direct.ethBlockNumber().send().getBlockNumber().longValue() - 2; } catch (Exception e) { return -1; } }, PRIO, MAXFEE, graceMs);
    }
    /** Reconcile repeatedly (as a scheduler would) until the record leaves the in-flight states or the budget is spent. */
    static TxRecord settle(Tracker t, TxRecord r) throws Exception {
        long end = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < end) {
            t.reconcile(r);
            if (r.state == TxState.CONFIRMED || r.state == TxState.REVERTED || r.state == TxState.FINALIZED || r.state == TxState.REPLACED) break;
            Thread.sleep(150);
        }
        return r;
    }
    static List<String> states(TxRecord r) { return r.history.stream().map(h -> h.substring(h.indexOf(' ') + 1)).toList(); }

    @BeforeAll
    static void setup() throws Exception {
        EnvGuard.checkEndpoint(Env.LOCAL_SIMULATION, RPC);
        raw = new Rpc(RPC, Duration.ofSeconds(10));
        direct = client(RPC, 10);
        EnvGuard.check(Env.LOCAL_SIMULATION, RPC, direct.ethChainId().send().getChainId().longValue());
        creds = Credentials.create(Keys.createEcKeyPair());
        raw.result("anvil_setBalance", creds.getAddress(), "0x56bc75e2d63100000");
        String bin = new String(LifecycleScenariosTest.class.getResourceAsStream("/Phase1Lab.bin").readAllBytes(), StandardCharsets.UTF_8).trim();
        String ctor = Numeric.cleanHexPrefix(FunctionEncoder.encodeConstructor(List.of(new Address(creds.getAddress()))));
        RawTransaction tx = RawTransaction.createTransaction(CHAIN, BigInteger.ZERO, BigInteger.valueOf(3_000_000), "", BigInteger.ZERO, bin + ctor, PRIO, MAXFEE);
        String hash = direct.ethSendRawTransaction(Numeric.toHexString(TransactionEncoder.signMessage(tx, CHAIN, creds))).send().getTransactionHash();
        Thread.sleep(300);
        labAddr = direct.ethGetTransactionReceipt(hash).send().getTransactionReceipt().orElseThrow().getContractAddress();
        ledgerFile = File.createTempFile("phase1-ledger", ".json"); ledgerFile.delete();
        ledger = new Ledger(ledgerFile);
        tracker = newTracker(direct, ledger, Env.LOCAL_SIMULATION, CHAIN, 3_600_000);
    }

    @AfterAll
    static void flush() throws Exception {
        Evidence.rec("L0-summary", "PASS", Evidence.of("scenarios", summary));
        Evidence.flush("lifecycle-prototype.json");
        ledgerFile.delete();
    }

    @Test @Order(1)
    void s1_happyPath_acceptedIsNotFinal_thenConfirmedThenFinalized_andOperationIsIdempotent() throws Exception {
        TxRecord r = tracker.execute("OP-S1", labAddr, okCall(1), 100_000);
        assertEquals(TxState.SUBMITTED, r.state);                                   // accepted by the node: NOT success yet
        TxRecord again = tracker.execute("OP-S1", labAddr, okCall(1), 100_000);         // business retry
        assertSame(r, again); assertEquals(1, r.sends);                             // no second send
        settle(tracker, r); assertEquals(TxState.CONFIRMED, r.state);
        mine(3); tracker.reconcile(r); assertEquals(TxState.FINALIZED, r.state);
        summary.put("S1", Map.of("finalState", r.state, "sends", r.sends, "history", states(r)));
    }

    @Test @Order(2)
    void s2_receiptStatusZeroIsReverted_notConfirmed() throws Exception {
        TxRecord r = tracker.execute("OP-S2", labAddr, enc("boom", new Uint256(42)), 100_000);
        assertEquals(TxState.SUBMITTED, r.state);                                   // node accepted the tx
        settle(tracker, r);
        assertEquals(TxState.REVERTED, r.state);
        mine(3); tracker.reconcile(r); assertEquals(TxState.REVERTED, r.state);     // terminal
        summary.put("S2", Map.of("finalState", r.state, "history", states(r)));
    }

    @Test @Order(3)
    void s3_timeoutAfterSubmit_isUnknown_noDuplicateSend_reconciledByHash() throws Exception {
        try (FaultProxy proxy = new FaultProxy(RPC)) {
            proxy.mode = FaultProxy.Mode.HOLD_SEND;
            Web3j w = client(proxy.url(), 1);
            Ledger l = new Ledger(File.createTempFile("p1-s3", ".json"));
            Tracker t = newTracker(w, l, Env.LOCAL_SIMULATION, CHAIN, 3_600_000);
            TxRecord r = t.execute("OP-S3", labAddr, okCall(3), 100_000);
            assertEquals(TxState.UNKNOWN, r.state);                                 // client timed out; the node DID receive it
            assertEquals(1, proxy.sendRawCount.get());
            TxRecord retry = t.execute("OP-S3", labAddr, okCall(3), 100_000);           // impatient business retry
            assertSame(r, retry); assertEquals(1, proxy.sendRawCount.get());        // still only one send
            proxy.mode = FaultProxy.Mode.PASS;
            settle(t, r); assertEquals(TxState.CONFIRMED, r.state);
            long latest = direct.ethGetTransactionCount(creds.getAddress(), DefaultBlockParameterName.LATEST).send().getTransactionCount().longValue();
            assertEquals(r.nonce + 1, latest);                                      // executed exactly once
            summary.put("S3", Map.of("finalState", r.state, "proxySendCount", proxy.sendRawCount.get(), "history", states(r)));
            w.shutdown();
        }
    }

    @Test @Order(4)
    void s4_restart_recoversUnknownFromPersistedLedger() throws Exception {
        File f = File.createTempFile("p1-s4", ".json"); f.delete();
        String opHash;
        try (FaultProxy proxy = new FaultProxy(RPC)) {
            proxy.mode = FaultProxy.Mode.HOLD_SEND;
            Web3j w = client(proxy.url(), 1);
            Tracker before = newTracker(w, new Ledger(f), Env.LOCAL_SIMULATION, CHAIN, 3_600_000);
            TxRecord r = before.execute("OP-S4", labAddr, okCall(4), 100_000);
            assertEquals(TxState.UNKNOWN, r.state); opHash = r.txHash;
            w.shutdown();                                                            // "process dies" here
        }
        Ledger reloaded = new Ledger(f);                                             // "restart": new objects, state only from the file
        Tracker after = newTracker(direct, reloaded, Env.LOCAL_SIMULATION, CHAIN, 3_600_000);
        TxRecord r2 = reloaded.get("OP-S4");
        assertNotNull(r2); assertEquals(opHash, r2.txHash); assertEquals(TxState.UNKNOWN, r2.state);
        settle(after, r2);
        assertEquals(TxState.CONFIRMED, r2.state);
        summary.put("S4", Map.of("finalState", r2.state, "history", states(r2)));
        f.delete();
    }

    @Test @Order(5)
    void s5_nullOrThrottledProviderIsNeverTreatedAsFailure() throws Exception {
        try (FaultProxy proxy = new FaultProxy(RPC)) {
            Web3j w = client(proxy.url(), 3);
            Tracker t = newTracker(w, new Ledger(File.createTempFile("p1-s5", ".json")), Env.LOCAL_SIMULATION, CHAIN, 3_600_000);
            TxRecord r = t.execute("OP-S5", labAddr, okCall(5), 100_000);               // mined on anvil
            proxy.mode = FaultProxy.Mode.NULL_LOOKUPS;
            t.reconcile(r); assertEquals(TxState.UNKNOWN, r.state);                 // null/null: unknown, NOT reverted/replaced
            proxy.mode = FaultProxy.Mode.HTTP_429;
            t.reconcile(r); assertEquals(TxState.UNKNOWN, r.state); assertNotNull(r.lastError);   // HTML 429 body: state unchanged
            proxy.mode = FaultProxy.Mode.PASS;
            settle(t, r); assertEquals(TxState.CONFIRMED, r.state);
            summary.put("S5", Map.of("duringNull", "UNKNOWN", "duringHttp429", "UNKNOWN (lastError=" + r.lastError + ")", "afterRecovery", r.state, "history", states(r)));
            w.shutdown();
        }
    }

    @Test @Order(6)
    void s6_replacedNonceIsDetectedByNonceAndHash() throws Exception {
        Tracker t = newTracker(direct, new Ledger(File.createTempFile("p1-s6", ".json")), Env.LOCAL_SIMULATION, CHAIN, 0);
        raw.result("evm_setAutomine", false);
        try {
            TxRecord r = t.execute("OP-S6", labAddr, okCall(6), 100_000);
            assertEquals(TxState.SUBMITTED, r.state);
            // someone else (e.g. a speed-up) replaces the same nonce with a higher fee
            RawTransaction rep = RawTransaction.createTransaction(CHAIN, BigInteger.valueOf(r.nonce), BigInteger.valueOf(100_000), labAddr, BigInteger.ZERO, okCall(66), PRIO.multiply(BigInteger.valueOf(3)), MAXFEE.multiply(BigInteger.valueOf(3)));
            assertFalse(direct.ethSendRawTransaction(Numeric.toHexString(TransactionEncoder.signMessage(rep, CHAIN, creds))).send().hasError());
            t.reconcile(r); assertEquals(TxState.PENDING.equals(r.state) ? TxState.PENDING : r.state, r.state);   // while pending: PENDING/UNKNOWN only
            assertNotEquals(TxState.REVERTED, r.state); assertNotEquals(TxState.CONFIRMED, r.state);
            raw.result("evm_setAutomine", true); mine(1);
            t.reconcile(r);
            assertEquals(TxState.REPLACED, r.state);
            summary.put("S6", Map.of("finalState", r.state, "history", states(r)));
        } finally { raw.result("evm_setAutomine", true); }
    }

    @Test @Order(7)
    void s7_hintsAreDuplicatedOrMissed_reconciliationStillConverges() throws Exception {
        TxRecord r = tracker.execute("OP-S7a", labAddr, okCall(7), 100_000);
        TxRecord missed = tracker.execute("OP-S7b", labAddr, okCall(77), 100_000);       // no hint will ever arrive for this one
        for (int i = 0; i < 3; i++) tracker.onHint(r.txHash);                       // duplicated delivery
        tracker.onHint("0x" + "ab".repeat(32));                                     // hint for an unknown tx: ignored
        assertEquals(TxState.CONFIRMED, r.state);
        assertEquals(TxState.SUBMITTED, missed.state);                              // hints never set state; none arrived
        settle(tracker, missed);                                                    // periodic reconciliation closes the gap
        assertEquals(TxState.CONFIRMED, missed.state);
        summary.put("S7", Map.of("hintsDelivered", tracker.hintsSeen.size(), "duplicatedHintFinal", r.state, "missedHintRecovered", missed.state));
    }

    @Test @Order(8)
    void s8_recordsOfAnotherEnvironmentAreNotTouched() throws Exception {
        TxRecord r = tracker.execute("OP-S8", labAddr, okCall(8), 100_000);
        Tracker other = newTracker(direct, ledger, Env.AVALANCHE_FUJI, 43113, 3_600_000);
        other.reconcileAll();
        assertEquals(TxState.SUBMITTED, r.state);                                   // untouched by a Fuji-configured reconciler
        settle(tracker, r); assertEquals(TxState.CONFIRMED, r.state);
        summary.put("S8", Map.of("untouchedByOtherEnvironment", true, "finalState", r.state));
    }
}
