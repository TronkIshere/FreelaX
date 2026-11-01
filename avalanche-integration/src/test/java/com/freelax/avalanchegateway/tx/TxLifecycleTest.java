package com.freelax.avalanchegateway.tx;

import com.freelax.avalanchegateway.config.TxProperties;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.signer.SignerRole;
import com.freelax.avalanchegateway.tx.SubmissionService.SubmitRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.web3j.protocol.core.DefaultBlockParameterName;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase 1 scenarios S1-S8 against a real Anvil and a real MySQL (Testcontainers), plus concurrency, replacement,
 * caps and the nonce-gap guard. Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class TxLifecycleTest {

    private static final String OK_TARGET = "0x1111111111111111111111111111111111111111";
    private static final String REVERT_TARGET = "0x2222222222222222222222222222222222222222";
    private static final long GAS = 100_000;

    private final List<Harness> harnesses = new ArrayList<>();
    private final List<FaultProxy> proxies = new ArrayList<>();

    @BeforeAll
    static void startStack() {
        LocalStack.start();
        AnvilRpc.setCode(LocalStack.anvilUrl(), OK_TARGET, "0x00");
        AnvilRpc.setCode(LocalStack.anvilUrl(), REVERT_TARGET, "0x60006000fd");
    }

    @BeforeEach
    void cleanDatabase() {
        new org.springframework.jdbc.core.JdbcTemplate(LocalStack.dataSource()).update("DELETE FROM avalanche_tx");
        new org.springframework.jdbc.core.JdbcTemplate(LocalStack.dataSource()).update("DELETE FROM avalanche_nonce");
        AnvilRpc.setAutomine(LocalStack.anvilUrl(), true);
    }

    @AfterEach
    void cleanUp() {
        AnvilRpc.setAutomine(LocalStack.anvilUrl(), true);
        harnesses.forEach(Harness::close);
        proxies.forEach(FaultProxy::close);
    }

    private Harness harness(String url, int timeoutMs, TxProperties props, SignerRegistry registry) {
        Harness h = new Harness(LocalStack.dataSource(), url, timeoutMs, props, registry);
        harnesses.add(h);
        return h;
    }

    private Harness fundedHarness(String url, TxProperties props) {
        SignerRegistry registry = Harness.newRegistry();
        for (SignerRole role : SignerRole.values()) {
            AnvilRpc.fund(LocalStack.anvilUrl(), registry.get(role).address());
        }
        return harness(url, 5000, props, registry);
    }

    private FaultProxy proxy() throws Exception {
        FaultProxy p = new FaultProxy(LocalStack.anvilUrl());
        proxies.add(p);
        return p;
    }

    private static SubmitRequest request(String key, String to) {
        return new SubmitRequest(key, SignerRole.RELAYER, to, "0x", GAS);
    }

    private static TxRecord reload(Harness h, TxRecord r) {
        return h.repository.findById(r.id()).orElseThrow();
    }

    /** Anvil's automine is asynchronous by a few milliseconds, so wait (reconciling each time) instead of asserting once. */
    private static TxRecord reconcileUntil(Harness h, long id, TxState target) {
        TxRecord record = null;
        for (int i = 0; i < 60; i++) {
            record = h.tracker.reconcile(id);
            if (record.state() == target) {
                return record;
            }
            sleep(100);
        }
        return record;
    }

    private static void awaitMined(long nonceExclusive, String address) {
        for (int i = 0; i < 60 && AnvilRpc.transactionCount(LocalStack.anvilUrl(), address, "latest") <= nonceExclusive; i++) {
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

    /** Creates the record exactly like submit() does (nonce + insert in one database transaction) but stops before signing. */
    private static TxRecord createOnly(Harness h, String key, SignerRole role, String to) {
        String address = h.registry.get(role).address();
        long floor = h.client.transactionCount(address, DefaultBlockParameterName.PENDING);
        return h.transactions.execute(status -> {
            long nonce = h.allocator.allocate("LOCAL_SIMULATION", 31337, address, floor, System.currentTimeMillis());
            long id = h.repository.insertCreated(key, "LOCAL_SIMULATION", 31337, role.name(), address, to, "0x", GAS,
                    BigInteger.valueOf(3_000_000_000L), BigInteger.valueOf(1_000_000_000L), nonce, System.currentTimeMillis());
            return h.repository.findById(id).orElseThrow();
        });
    }

    // ---- S1 happy path, persist-before-send, idempotency -----------------------------------------------------------

    @Test
    void s1_happyPath_persistsBeforeSend_isIdempotent_andOnlyFinalizedAfterThePolicy() throws Exception {
        FaultProxy proxy = proxy();
        Harness h = fundedHarness(proxy.url(), Harness.props(30_000, 2));
        AtomicReference<TxRecord> atSend = new AtomicReference<>();
        proxy.onSend = raw -> atSend.set(h.repository.find("LOCAL_SIMULATION", 31337, "op-s1").orElse(null));

        TxRecord submitted = h.submission.submit(request("op-s1", OK_TARGET));

        assertEquals(TxState.SUBMITTED, submitted.state(), "acceptance is SUBMITTED, never success");
        assertNotNull(atSend.get(), "the row existed when the node received the transaction");
        assertEquals(TxState.SIGNED, atSend.get().state());
        assertEquals(submitted.rawTx(), atSend.get().rawTx(), "raw transaction persisted before send");
        assertEquals(submitted.txHash(), atSend.get().txHash());

        TxRecord confirmed = reconcileUntil(h, submitted.id(), TxState.CONFIRMED);
        assertEquals(TxState.CONFIRMED, confirmed.state());
        assertEquals(1, confirmed.receiptStatus());
        assertNotNull(confirmed.blockNumber());

        AnvilRpc.mine(LocalStack.anvilUrl());
        assertEquals(TxState.FINALIZED, reconcileUntil(h, submitted.id(), TxState.FINALIZED).state());

        TxRecord again = h.submission.submit(request("op-s1", OK_TARGET));
        assertEquals(submitted.id(), again.id());
        assertEquals(submitted.nonce(), again.nonce());
        assertEquals(1, proxy.sendCount.get(), "one transaction per business operation");
        assertEquals(submitted.nonce() + 1, h.allocator.peekNext("LOCAL_SIMULATION", 31337, submitted.signerAddress()));
    }

    // ---- S2 reverted -----------------------------------------------------------------------------------------------

    @Test
    void s2_receiptStatusZeroIsRevertedAndNeverConfirmed() {
        Harness h = fundedHarness(LocalStack.anvilUrl(), Harness.props(30_000, 2));
        TxRecord submitted = h.submission.submit(request("op-s2", REVERT_TARGET));
        TxRecord result = reconcileUntil(h, submitted.id(), TxState.REVERTED);
        assertEquals(TxState.REVERTED, result.state());
        assertEquals(0, result.receiptStatus());
        assertTrue(result.state().terminal());
    }

    // ---- S3 timeout after submit, S4 restart -----------------------------------------------------------------------

    @Test
    void s3_timeoutAfterSubmitIsUnknown_noSecondSend_thenReconcilesByHash_andSurvivesRestart() throws Exception {
        FaultProxy proxy = proxy();
        SignerRegistry registry = Harness.newRegistry();
        AnvilRpc.fund(LocalStack.anvilUrl(), registry.get(SignerRole.RELAYER).address());
        Harness h = harness(proxy.url(), 1500, Harness.props(30_000, 2), registry);
        proxy.holdSendResponseMs = 4000;

        TxRecord first = h.submission.submit(request("op-s3", OK_TARGET));
        assertEquals(TxState.UNKNOWN, first.state(), "a timeout is never a failure");
        assertEquals(ErrorClass.TRANSPORT.name(), first.lastErrorClass());

        TxRecord retry = h.submission.submit(request("op-s3", OK_TARGET));
        assertEquals(first.id(), retry.id());
        assertEquals(1, proxy.sendCount.get(), "an impatient retry must not send again");

        proxy.reset();
        Harness restarted = harness(proxy.url(), 5000, Harness.props(30_000, 2), registry);
        TxRecord recovered = reconcileUntil(restarted, first.id(), TxState.CONFIRMED);
        assertEquals(TxState.CONFIRMED, recovered.state(), "a new process reconciles the persisted UNKNOWN record");
        assertEquals(first.txHash(), recovered.txHash());
        assertEquals(first.nonce() + 1, restarted.allocator.peekNext("LOCAL_SIMULATION", 31337, first.signerAddress()), "nonce advanced exactly once");
        assertEquals(1, proxy.sendCount.get());
    }

    @Test
    void s4_restartResendsTheSamePersistedRawTransaction_andResumesCreatedRecords() throws Exception {
        SignerRegistry registry = Harness.newRegistry();
        AnvilRpc.fund(LocalStack.anvilUrl(), registry.get(SignerRole.RELAYER).address());
        AnvilRpc.fund(LocalStack.anvilUrl(), registry.get(SignerRole.ADMIN).address());
        Harness before = harness(LocalStack.anvilUrl(), 5000, Harness.props(0, 2), registry);

        // crash after the raw transaction was persisted but before it was sent
        TxRecord created = createOnly(before, "op-s4-signed", SignerRole.RELAYER, OK_TARGET);
        TransactionSigning.Signed signed = TransactionSigning.sign(created, registry.get(SignerRole.RELAYER));
        TxRecord signedRow = before.transitions.apply(created, TxState.SIGNED, "raw_tx", signed.rawHex(), "tx_hash", signed.txHash());
        // crash right after the nonce was allocated, before anything was signed
        TxRecord createdOnly = createOnly(before, "op-s4-created", SignerRole.ADMIN, OK_TARGET);

        Harness after = harness(LocalStack.anvilUrl(), 5000, Harness.props(0, 2), registry);
        TxRecord resent = after.tracker.reconcile(signedRow.id());
        assertEquals(TxState.SUBMITTED, resent.state());
        assertEquals(signed.txHash(), resent.txHash(), "the same bytes, hence the same hash, are resent");
        assertEquals(TxState.CONFIRMED, reconcileUntil(after, signedRow.id(), TxState.CONFIRMED).state());

        TxRecord resumed = after.tracker.reconcile(createdOnly.id());
        assertEquals(TxState.SUBMITTED, resumed.state(), "CREATED is signed with the same nonce and sent");
        assertEquals(createdOnly.nonce(), resumed.nonce());
    }

    @Test
    void s4_createdRecordOfAnEphemeralSignerThatIsGoneFailsWithoutCreatingANonceGap() {
        SignerRegistry gone = Harness.newRegistry();
        Harness before = harness(LocalStack.anvilUrl(), 5000, Harness.props(0, 2), gone);
        AnvilRpc.fund(LocalStack.anvilUrl(), gone.get(SignerRole.RELAYER).address());
        TxRecord created = createOnly(before, "op-s4-gone", SignerRole.RELAYER, OK_TARGET);

        Harness restartedWithNewKeys = harness(LocalStack.anvilUrl(), 5000, Harness.props(0, 2), Harness.newRegistry());
        TxRecord result = restartedWithNewKeys.tracker.reconcile(created.id());
        assertEquals(TxState.FAILED, result.state());
        assertEquals(ErrorClass.SIGNER_UNAVAILABLE.name(), result.lastErrorClass());
        assertFalse(result.nonceGap(), "the old address will never send again, so no gap matters");
    }

    // ---- S5 null lookups and HTTP 429 -----------------------------------------------------------------------------

    @Test
    void s5_nullLookupsAndHttp429NeverBecomeFailureOrReplacement() throws Exception {
        FaultProxy proxy = proxy();
        Harness h = fundedHarness(proxy.url(), Harness.props(3_600_000, 2));
        TxRecord submitted = h.submission.submit(request("op-s5", OK_TARGET));
        awaitMined(submitted.nonce(), submitted.signerAddress());

        proxy.nullLookups = true;
        TxRecord afterNull = h.tracker.reconcile(submitted.id());
        assertEquals(TxState.SUBMITTED, afterNull.state(), "null receipt and null by-hash prove nothing");
        assertEquals(1, afterNull.missingObservations());

        proxy.nullLookups = false;
        proxy.rateLimited = true;
        TxRecord afterThrottle = h.tracker.reconcile(submitted.id());
        assertEquals(TxState.SUBMITTED, afterThrottle.state(), "HTTP 429 is never a failure");
        assertEquals(ErrorClass.RATE_LIMITED.name(), afterThrottle.lastErrorClass());

        proxy.reset();
        TxRecord recovered = reconcileUntil(h, submitted.id(), TxState.CONFIRMED);
        assertEquals(TxState.CONFIRMED, recovered.state());
        assertEquals(0, recovered.missingObservations());
        assertNull(recovered.lastErrorClass());
    }

    // ---- S6 replaced nonce, speed-up ------------------------------------------------------------------------------

    @Test
    void s6_replacedOnlyAfterNullNullNonceConsumedGraceAndRepeatedObservations() {
        Harness h = fundedHarness(LocalStack.anvilUrl(), Harness.props(0, 2));
        AnvilRpc.setAutomine(LocalStack.anvilUrl(), false);
        TxRecord a = h.submission.submit(request("op-s6", OK_TARGET));
        assertEquals(TxState.SUBMITTED, a.state());

        // a foreign transaction from the same key reuses the nonce with a much higher fee
        TxRecord foreign = new TxRecord(a.id() + 9000, "foreign", a.environment(), a.chainId(), a.signerRole(), a.signerAddress(),
                a.toAddress(), "0xabcd", a.gasLimit(), a.maxFeePerGas().multiply(BigInteger.valueOf(5)),
                a.maxPriorityFeePerGas().multiply(BigInteger.valueOf(5)), a.nonce(), null, null, null, TxState.CREATED,
                null, null, null, null, null, 0, false, 0, 0, 0, 0, null, null);
        TransactionSigning.Signed foreignSigned = TransactionSigning.sign(foreign, h.registry.get(SignerRole.RELAYER));
        assertTrue(h.client.sendRawTransaction(foreignSigned.rawHex()).accepted());

        // The original is no longer in the pool (replaced) and the nonce is not consumed yet: the tracker rebroadcasts the same
        // raw transaction, which the node refuses as underpriced. That is not a failure and not yet a replacement.
        TxRecord rebroadcast = h.tracker.reconcile(a.id());
        assertEquals(TxState.SUBMITTED, rebroadcast.state());
        assertEquals(ErrorClass.UNDERPRICED.name(), rebroadcast.lastErrorClass());
        AnvilRpc.mine(LocalStack.anvilUrl());

        TxRecord first = h.tracker.reconcile(a.id());
        assertEquals(1, first.missingObservations());
        assertNotEquals(TxState.REPLACED, first.state(), "one observation is not enough");
        TxRecord second = h.tracker.reconcile(a.id());
        assertEquals(TxState.REPLACED, second.state());
        assertTrue(second.state().terminal());
    }

    @Test
    void speedUpKeepsTheNonceAndTheEarlierHashAndConfirmsWhicheverMines() {
        Harness h = fundedHarness(LocalStack.anvilUrl(), Harness.props(30_000, 2));
        AnvilRpc.setAutomine(LocalStack.anvilUrl(), false);
        TxRecord a = h.submission.submit(request("op-bump", OK_TARGET));

        TxRecord bumped = h.submission.speedUp("op-bump");
        assertEquals(TxState.SUBMITTED, bumped.state());
        assertEquals(a.nonce(), bumped.nonce(), "a replacement keeps the nonce");
        assertNotEquals(a.txHash(), bumped.txHash());
        assertEquals(a.txHash(), bumped.previousTxHashes());
        assertTrue(bumped.maxFeePerGas().compareTo(a.maxFeePerGas()) > 0);

        AnvilRpc.mine(LocalStack.anvilUrl());
        TxRecord confirmed = h.tracker.reconcile(a.id());
        assertEquals(TxState.CONFIRMED, confirmed.state());
        assertEquals(bumped.txHash(), confirmed.txHash());
        assertEquals(a.txHash(), confirmed.previousTxHashes());
    }

    @Test
    void anUnderpricedReplacementIsNotAFailureAndTheOriginalStillConfirms() {
        Harness h = fundedHarness(LocalStack.anvilUrl(), Harness.props(30_000, 2));
        AnvilRpc.setAutomine(LocalStack.anvilUrl(), false);
        TxRecord a = h.submission.submit(request("op-underpriced", OK_TARGET));

        // What a too-small speed-up produces: same nonce and same fees, different bytes (Anvil accepts any strictly higher fee,
        // so the equal-fee case is the real node's "replacement transaction underpriced").
        TxRecord candidate = new TxRecord(a.id(), a.operationKey(), a.environment(), a.chainId(), a.signerRole(), a.signerAddress(),
                a.toAddress(), a.data(), a.gasLimit() + 1, a.maxFeePerGas(), a.maxPriorityFeePerGas(), a.nonce(), null, null, null,
                TxState.CREATED, null, null, null, null, null, 0, false, 0, 0, 0, 0, null, null);
        TransactionSigning.Signed replacement = TransactionSigning.sign(candidate, h.registry.get(SignerRole.RELAYER));
        TxRecord signedRow = h.transitions.apply(a, TxState.SIGNED, "gas_limit", a.gasLimit() + 1, "raw_tx", replacement.rawHex(),
                "tx_hash", replacement.txHash(), "previous_tx_hashes", a.txHash());

        TxRecord afterSend = h.submission.send(signedRow);
        assertEquals(TxState.SIGNED, afterSend.state(), "rejected at admission: not on chain, not failed");
        assertEquals(ErrorClass.UNDERPRICED.name(), afterSend.lastErrorClass());

        AnvilRpc.mine(LocalStack.anvilUrl());
        TxRecord confirmed = reconcileUntil(h, a.id(), TxState.CONFIRMED);
        assertEquals(TxState.CONFIRMED, confirmed.state());
        assertEquals(a.txHash(), confirmed.txHash(), "the original hash is the one that mined");
        assertEquals(replacement.txHash(), confirmed.previousTxHashes());
    }

    // ---- S8 environment isolation ---------------------------------------------------------------------------------

    @Test
    void s8_recordsOfAnotherEnvironmentAreInvisibleAndUntouched() {
        Harness h = fundedHarness(LocalStack.anvilUrl(), Harness.props(0, 2));
        h.jdbc.update("INSERT INTO avalanche_tx (operation_key, environment, chain_id, signer_role, signer_address, to_address, data,"
                + " gas_limit, max_fee_per_gas, max_priority_fee_per_gas, nonce, tx_hash, raw_tx, state, created_at_ms, updated_at_ms)"
                + " VALUES ('other-env','AVALANCHE_FUJI',43113,'RELAYER','0x3333333333333333333333333333333333333333',"
                + "'0x1111111111111111111111111111111111111111','0x',100000,1,1,0,"
                + "'0x" + "ab".repeat(32) + "','0x00','SUBMITTED',1,1)");
        TxRecord other = h.repository.find("AVALANCHE_FUJI", 43113, "other-env").orElseThrow();

        assertTrue(h.repository.inFlight("LOCAL_SIMULATION", 31337).isEmpty());
        assertTrue(h.tracker.reconcileInFlight().isEmpty());
        TxRecord untouched = h.tracker.reconcile(other);
        assertEquals(other.version(), reload(h, other).version());
        assertEquals(TxState.SUBMITTED, untouched.state());
        assertTrue(h.repository.find("LOCAL_SIMULATION", 31337, "other-env").isEmpty());
    }

    // ---- recoverable admission errors, permanent rejection, caps, conflicts ----------------------------------------

    @Test
    void insufficientFundsKeepsTheNonceAndIsResentAfterFunding() {
        SignerRegistry registry = Harness.newRegistry();   // deliberately not funded
        Harness h = harness(LocalStack.anvilUrl(), 5000, Harness.props(0, 2), registry);
        TxRecord submitted = h.submission.submit(request("op-poor", OK_TARGET));
        assertEquals(TxState.SIGNED, submitted.state());
        assertEquals(ErrorClass.INSUFFICIENT_FUNDS.name(), submitted.lastErrorClass());

        AnvilRpc.fund(LocalStack.anvilUrl(), registry.get(SignerRole.RELAYER).address());
        TxRecord resent = h.tracker.reconcile(submitted.id());
        assertEquals(TxState.SUBMITTED, resent.state());
        assertEquals(submitted.nonce(), resent.nonce());
        assertEquals(submitted.txHash(), resent.txHash());
        assertEquals(TxState.CONFIRMED, reconcileUntil(h, submitted.id(), TxState.CONFIRMED).state());
    }

    @Test
    void permanentRejectionFailsAndFlagsANonceGapThatBlocksFurtherAllocation() {
        // Anvil does not enforce intrinsic gas at admission, but it does refuse a gas limit above the block gas limit.
        TxProperties highCap = new TxProperties(true, 100_000_000, 200, 1, 30, 1, 0, 2, 0);
        Harness h = fundedHarness(LocalStack.anvilUrl(), highCap);
        TxRecord failed = h.submission.submit(new SubmitRequest("op-perm", SignerRole.RELAYER, OK_TARGET, "0x", 60_000_000));
        assertEquals(TxState.FAILED, failed.state());
        assertEquals(ErrorClass.PERMANENT_REJECTION.name(), failed.lastErrorClass());
        assertTrue(failed.nonceGap());

        assertThrows(NonceGapException.class, () -> h.submission.submit(request("op-after-gap", OK_TARGET)));
        // another signer is unaffected
        TxRecord other = h.submission.submit(new SubmitRequest("op-other-signer", SignerRole.ADMIN, OK_TARGET, "0x", GAS));
        assertEquals(TxState.SUBMITTED, other.state());
    }

    @Test
    void requestValidationAndCapsRejectBeforeAnyNonceIsConsumed() {
        TxProperties capped = new TxProperties(true, 200_000, 2, 2, 30, 1, 30_000, 2, 0);   // tip equals the cap, so the max fee is always exactly the cap
        Harness h = fundedHarness(LocalStack.anvilUrl(), capped);
        String relayer = h.registry.get(SignerRole.RELAYER).address();

        assertThrows(SubmissionService.InvalidRequestException.class, () -> h.submission.submit(new SubmitRequest("k", SignerRole.RELAYER, OK_TARGET, "0x", 200_001)));
        assertThrows(SubmissionService.InvalidRequestException.class, () -> h.submission.submit(new SubmitRequest("k", SignerRole.RELAYER, OK_TARGET, "0x", 20_999)));
        assertThrows(SubmissionService.InvalidRequestException.class, () -> h.submission.submit(new SubmitRequest("bad key!", SignerRole.RELAYER, OK_TARGET, "0x", GAS)));
        assertThrows(SubmissionService.InvalidRequestException.class, () -> h.submission.submit(new SubmitRequest("k", SignerRole.RELAYER, "0x123", "0x", GAS)));
        assertThrows(SubmissionService.InvalidRequestException.class, () -> h.submission.submit(new SubmitRequest("k", SignerRole.RELAYER, OK_TARGET, "0xabc", GAS)));
        assertThrows(SubmissionService.InvalidRequestException.class, () -> h.submission.submit(new SubmitRequest("k", null, OK_TARGET, "0x", GAS)));
        assertNull(h.allocator.peekNext("LOCAL_SIMULATION", 31337, relayer), "no nonce was consumed");
        assertEquals(0, h.jdbc.queryForObject("SELECT COUNT(*) FROM avalanche_tx", Integer.class));

        AnvilRpc.setAutomine(LocalStack.anvilUrl(), false);
        TxRecord ok = h.submission.submit(request("op-capped", OK_TARGET));
        assertTrue(ok.maxFeePerGas().compareTo(BigInteger.valueOf(2_000_000_000L)) <= 0, "the fee cap holds");
        assertThrows(SubmissionService.FeeCapExceededException.class, () -> h.submission.speedUp("op-capped"));
    }

    @Test
    void theSameOperationKeyWithDifferentParametersIsAConflictAndSendsNothing() throws Exception {
        FaultProxy proxy = proxy();
        Harness h = fundedHarness(proxy.url(), Harness.props(30_000, 2));
        TxRecord first = h.submission.submit(request("op-conflict", OK_TARGET));
        assertThrows(SubmissionService.IdempotencyConflictException.class, () -> h.submission.submit(request("op-conflict", REVERT_TARGET)));
        assertThrows(SubmissionService.IdempotencyConflictException.class,
                () -> h.submission.submit(new SubmitRequest("op-conflict", SignerRole.ADMIN, OK_TARGET, "0x", GAS)));
        assertEquals(1, proxy.sendCount.get());
        assertEquals(first.id(), h.repository.find("LOCAL_SIMULATION", 31337, "op-conflict").orElseThrow().id());
    }

    @Test
    void aProcessWithoutSignersCannotSubmit() {
        Harness h = fundedHarness(LocalStack.anvilUrl(), Harness.props(30_000, 2));
        SubmissionService noSigners = new SubmissionService(h.client, Harness.LOCAL, null, h.repository, h.allocator,
                h.transactions, h.properties, new ErrorClassifier(), h.transitions, java.time.Clock.systemUTC());
        assertThrows(SubmissionService.SignerUnavailableException.class, () -> noSigners.submit(request("op-nosigner", OK_TARGET)));
    }

    // ---- second provider cross-check ------------------------------------------------------------------------------

    @Test
    void aSecondProviderOfTheSameEnvironmentPreventsAFalseReplacedWhenThePrimaryAnswersNull() throws Exception {
        FaultProxy primary = proxy();
        primary.nullLookups = true;
        SignerRegistry registry = Harness.newRegistry();
        AnvilRpc.fund(LocalStack.anvilUrl(), registry.get(SignerRole.RELAYER).address());
        Harness withoutSecondary = harness(primary.url(), 5000, Harness.props(0, 1), registry);
        Harness withSecondary = new Harness(LocalStack.dataSource(), primary.url(), 5000, Harness.props(0, 1), registry,
                com.freelax.avalanchegateway.chain.ChainClients.create(
                        com.freelax.avalanchegateway.env.AvalancheEnvironment.LOCAL_SIMULATION, LocalStack.anvilUrl(), 5000, 0));
        harnesses.add(withSecondary);

        TxRecord one = withoutSecondary.submission.submit(request("op-xcheck-1", OK_TARGET));
        TxRecord two = withoutSecondary.submission.submit(request("op-xcheck-2", OK_TARGET));
        awaitMined(two.nonce(), two.signerAddress());

        assertEquals(TxState.REPLACED, withoutSecondary.tracker.reconcile(one.id()).state(),
                "control: a lying primary alone drives the record to REPLACED");
        TxRecord confirmed = withSecondary.tracker.reconcile(two.id());
        assertEquals(TxState.CONFIRMED, confirmed.state(), "the second provider found the receipt the primary denied");
        assertEquals(1, confirmed.receiptStatus());
    }

    // ---- concurrency ----------------------------------------------------------------------------------------------

    @Test
    void concurrentSubmissionsNeverReuseANonceAndDuplicateKeysSendOnce() throws Exception {
        Harness h = fundedHarness(LocalStack.anvilUrl(), Harness.props(30_000, 2));
        String relayer = h.registry.get(SignerRole.RELAYER).address();
        int distinct = 40;
        int duplicates = 10;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<TxRecord>> jobs = new ArrayList<>();
            for (int i = 0; i < distinct; i++) {
                String key = "op-concurrent-" + i;
                jobs.add(() -> h.submission.submit(request(key, OK_TARGET)));
            }
            for (int i = 0; i < duplicates; i++) {
                jobs.add(() -> h.submission.submit(request("op-concurrent-dup", OK_TARGET)));
            }
            List<Future<TxRecord>> futures = pool.invokeAll(jobs);
            Set<Long> nonces = new HashSet<>();
            Set<Long> duplicateIds = new HashSet<>();
            for (int i = 0; i < futures.size(); i++) {
                TxRecord r = futures.get(i).get();
                if (i < distinct) {
                    assertTrue(nonces.add(r.nonce()), "nonce reused: " + r.nonce());
                } else {
                    duplicateIds.add(r.id());
                }
            }
            assertEquals(1, duplicateIds.size(), "all duplicates resolve to one record");
            TxRecord dup = h.repository.find("LOCAL_SIMULATION", 31337, "op-concurrent-dup").orElseThrow();
            assertTrue(nonces.add(dup.nonce()), "the duplicate key holds its own distinct nonce");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(distinct + 1, h.jdbc.queryForObject("SELECT COUNT(DISTINCT nonce) FROM avalanche_tx WHERE signer_address = ?", Integer.class, relayer));
        long max = h.jdbc.queryForObject("SELECT MAX(nonce) FROM avalanche_tx WHERE signer_address = ?", Long.class, relayer);
        assertEquals(distinct, max, "nonces are contiguous from zero");
        assertEquals(distinct + 1L, h.allocator.peekNext("LOCAL_SIMULATION", 31337, relayer));

        for (int pass = 0; pass < 40 && !h.repository.inFlight("LOCAL_SIMULATION", 31337).stream()
                .allMatch(r -> r.state() == TxState.CONFIRMED); pass++) {
            h.tracker.reconcileInFlight();
            Thread.sleep(100);
        }
        List<TxRecord> all = h.repository.inFlight("LOCAL_SIMULATION", 31337);
        assertTrue(all.stream().allMatch(r -> r.state() == TxState.CONFIRMED),
                "every concurrent submission mined: " + all.stream().map(TxRecord::state).toList());
        assertEquals(distinct + 1L, AnvilRpc.transactionCount(LocalStack.anvilUrl(), relayer, "latest"));
    }
}
