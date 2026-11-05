package com.freelax.avalanchegateway.tx;

import com.freelax.avalanchegateway.dto.request.SubmitRequest;
import com.freelax.avalanchegateway.entity.TxRecord;
import com.freelax.avalanchegateway.entity.TxState;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.signer.SignerRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Register item A3 (gateway half): the faults that the per-scenario tests in {@link TxLifecycleTest} inject one at a time,
 * combined in ONE sequence against a real Anvil and MySQL, with a single signer and a single fault proxy: timeout after
 * submission, impatient duplicate, revert, HTTP 429 storm, missed (null) lookups, process restart, duplicate after restart.
 * The invariants checked across the whole sequence: one network send per business operation, nonce advanced exactly once per
 * operation, no fault ever produces FAILED or REPLACED, a timeout is UNKNOWN (never success or failure), only a receipt
 * leads to CONFIRMED/REVERTED, and FINALIZED needs the confirmation policy. LOCAL_SIMULATION only; skipped without Docker.
 * The marketplace half of A3 (fault injection seen through the marketplace) is not covered here.
 */
@Testcontainers(disabledWithoutDocker = true)
class CombinedFaultScenarioTest {

    private static final String OK_TARGET = "0x3333333333333333333333333333333333333333";
    private static final String REVERT_TARGET = "0x4444444444444444444444444444444444444444";
    private static final long GAS = 100_000;
    /** Replacement grace far in the future: null lookups must not be able to reach REPLACED in this scenario. */
    private static final long GRACE_MS = 3_600_000;

    private final List<Harness> harnesses = new ArrayList<>();
    private final List<FaultProxy> proxies = new ArrayList<>();

    @BeforeAll
    static void startStack() {
        LocalStack.start();
        AnvilRpc.setCode(LocalStack.anvilUrl(), OK_TARGET, "0x00");
        AnvilRpc.setCode(LocalStack.anvilUrl(), REVERT_TARGET, "0x60006000fd");
    }

    @AfterEach
    void cleanUp() {
        AnvilRpc.setAutomine(LocalStack.anvilUrl(), true);
        harnesses.forEach(Harness::close);
        proxies.forEach(FaultProxy::close);
    }

    private Harness harness(String url, int timeoutMs, SignerRegistry registry) {
        Harness h = new Harness(LocalStack.dataSource(), url, timeoutMs, Harness.props(GRACE_MS, 2), registry);
        harnesses.add(h);
        return h;
    }

    private static SubmitRequest request(String key, String to) {
        return new SubmitRequest(key, SignerRole.RELAYER, to, "0x", GAS);
    }

    private static TxRecord reconcileUntil(Harness h, long id, TxState target) {
        TxRecord record = null;
        for (int i = 0; i < 80; i++) {
            record = h.tracker.reconcile(id);
            if (record.state() == target) {
                return record;
            }
            sleep(100);
        }
        return record;
    }

    /** Reconciles until the record leaves the given non-terminal states (or the attempts run out). */
    private static TxRecord reconcileUntilNot(Harness h, long id, EnumSet<TxState> waiting) {
        TxRecord record = null;
        for (int i = 0; i < 80; i++) {
            record = h.tracker.reconcile(id);
            if (!waiting.contains(record.state())) {
                return record;
            }
            sleep(100);
        }
        return record;
    }

    private static void awaitNonce(String address, long countAtLeast) {
        for (int i = 0; i < 80 && AnvilRpc.transactionCount(LocalStack.anvilUrl(), address, "latest") < countAtLeast; i++) {
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

    private static void assertNeverFailedOrReplaced(TxRecord r, String when) {
        assertFalse(EnumSet.of(TxState.FAILED, TxState.REPLACED).contains(r.state()), when + ": a fault produced " + r.state());
    }

    @Test
    void combinedFaults_keepOneSendPerOperation_neverFailOrReplace_andRecoverAfterRestart() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        String keyTimeout = "op-a3-timeout-" + run;
        String keyRevert = "op-a3-revert-" + run;
        FaultProxy proxy = new FaultProxy(LocalStack.anvilUrl());
        proxies.add(proxy);
        SignerRegistry registry = Harness.newRegistry();
        AnvilRpc.fund(LocalStack.anvilUrl(), registry.get(SignerRole.RELAYER).address());
        Harness h = harness(proxy.url(), 1500, registry);

        // 1. timeout after submission: the node receives the transaction, the gateway does not see the answer
        proxy.holdSendResponseMs = 4000;
        TxRecord timedOut = h.submission.submit(request(keyTimeout, OK_TARGET));
        assertEquals(TxState.UNKNOWN, timedOut.state(), "a timeout is never success or failure");
        assertEquals(ErrorClass.TRANSPORT.name(), timedOut.lastErrorClass());
        String signer = timedOut.signerAddress();
        long firstNonce = timedOut.nonce();

        // 2. impatient duplicate of the same business operation while the answer is still outstanding
        TxRecord duplicate = h.submission.submit(request(keyTimeout, OK_TARGET));
        assertEquals(timedOut.id(), duplicate.id());
        assertEquals(1, proxy.sendCount.get(), "a duplicate must not send again");
        proxy.holdSendResponseMs = 0;
        awaitNonce(signer, firstNonce + 1);

        // 3. a second operation that reverts on chain, sent normally through the same proxy and signer
        TxRecord revert = h.submission.submit(request(keyRevert, REVERT_TARGET));
        assertEquals(firstNonce + 1, revert.nonce(), "the next operation takes the next nonce; no gap, no reuse");
        assertEquals(2, proxy.sendCount.get());

        // 4. HTTP 429 storm while reconciling: never a failure, nothing resent
        proxy.rateLimited = true;
        TxRecord throttledA = h.tracker.reconcile(timedOut.id());
        TxRecord throttledB = h.tracker.reconcile(revert.id());
        assertEquals(ErrorClass.RATE_LIMITED.name(), throttledA.lastErrorClass());
        assertEquals(TxState.UNKNOWN, throttledA.state(), "429 leaves the timed-out record UNKNOWN");
        assertNeverFailedOrReplaced(throttledB, "429");
        proxy.rateLimited = false;

        // 5. missed lookups (provider answers null for receipt and by-hash): null proves nothing
        proxy.nullLookups = true;
        TxRecord nullA = h.tracker.reconcile(timedOut.id());
        TxRecord nullB = h.tracker.reconcile(revert.id());
        assertEquals(TxState.UNKNOWN, nullA.state(), "null lookups leave the timed-out record UNKNOWN");
        assertNeverFailedOrReplaced(nullB, "null lookups");
        assertEquals(2, proxy.sendCount.get(), "no fault so far triggered a resend");

        // 6. process restart with all faults cleared: a new process reconciles both persisted records from receipts
        proxy.reset();
        Harness restarted = harness(proxy.url(), 5000, registry);
        // The reverting transaction of step 3 was mined after this one, so the confirmation depth may already be reached:
        // CONFIRMED and FINALIZED are both correct here; FINALIZED is allowed only with a later block on top of the receipt.
        TxRecord confirmedA = reconcileUntilNot(restarted, timedOut.id(), EnumSet.of(TxState.UNKNOWN, TxState.SUBMITTED, TxState.PENDING));
        assertTrue(EnumSet.of(TxState.CONFIRMED, TxState.FINALIZED).contains(confirmedA.state()),
                "the UNKNOWN record is resolved by its receipt, was " + confirmedA.state());
        if (confirmedA.state() == TxState.FINALIZED) {
            assertTrue(AnvilRpc.blockNumber(LocalStack.anvilUrl()) > confirmedA.blockNumber(), "FINALIZED needs a later block");
        }
        assertEquals(timedOut.txHash(), confirmedA.txHash(), "the persisted hash is the one that was mined");
        assertEquals(1, confirmedA.receiptStatus());
        TxRecord revertedB = reconcileUntil(restarted, revert.id(), TxState.REVERTED);
        assertEquals(TxState.REVERTED, revertedB.state(), "receipt status 0 is REVERTED, never CONFIRMED");
        assertEquals(0, revertedB.receiptStatus());

        // 7. FINALIZED only after the confirmation policy; the error class of the earlier faults is cleared
        AnvilRpc.mine(LocalStack.anvilUrl());
        TxRecord finalizedA = reconcileUntil(restarted, timedOut.id(), TxState.FINALIZED);
        assertEquals(TxState.FINALIZED, finalizedA.state());
        assertNull(finalizedA.lastErrorClass());
        assertEquals(TxState.REVERTED, restarted.tracker.reconcile(revert.id()).state(), "REVERTED is terminal");

        // 8. duplicate after restart: idempotent, still one send per operation, nonce advanced exactly once per operation
        TxRecord afterRestart = restarted.submission.submit(request(keyTimeout, OK_TARGET));
        assertEquals(timedOut.id(), afterRestart.id());
        assertEquals(2, proxy.sendCount.get(), "two business operations, two sends, across all faults and a restart");
        assertEquals(firstNonce + 2, restarted.allocator.peekNext("LOCAL_SIMULATION", 31337, signer));
    }
}
