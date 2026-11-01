package lab.phase1.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import lab.phase1.EnvGuard.Env;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

import java.io.File;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.function.LongSupplier;

/**
 * ISOLATED RESEARCH PROTOTYPE (Phase 1 Part B task E). Not production code, not wired to any FreelaX service.
 * Demonstrates the lifecycle principles: accepted != success, receipt status 0 = REVERTED, timeout != failure,
 * unknown outcomes reconciled by tx hash + nonce, idempotency per business operation, events are hints only.
 */
public final class Lifecycle {
    private Lifecycle() { }

    /** REPLACED is an addition to the required list: the nonce was consumed by a different transaction (definitive for THIS attempt). */
    public enum TxState { CREATED, SUBMITTED, PENDING, CONFIRMED, FINALIZED, REVERTED, UNKNOWN, REPLACED }

    public static final class TxRecord {
        public String operationKey, environment, signer, txHash, rawTx, lastError;
        public long chainId, nonce, blockNumber = -1, firstNullAtMs = 0;
        public TxState state = TxState.CREATED;
        public int sends = 0, rebroadcasts = 0;
        public List<String> history = new ArrayList<>();

        void to(TxState s, String why) { history.add(Instant.now() + " " + state + "->" + s + " " + why); state = s; }
    }

    /** JSON-file ledger: survives a restart; records carry environment + chainId and are ignored by other environments. */
    public static final class Ledger {
        private final File file; private final ObjectMapper m = new ObjectMapper();
        private Map<String, TxRecord> byOp = new LinkedHashMap<>();

        public Ledger(File f) {
            this.file = f;
            try { if (f.exists()) { var t = m.readTree(f); t.fieldNames().forEachRemaining(k -> { try { byOp.put(k, m.treeToValue(t.get(k), TxRecord.class)); } catch (Exception e) { throw new RuntimeException(e); } }); } }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        public synchronized TxRecord get(String op) { return byOp.get(op); }
        public synchronized Collection<TxRecord> all() { return new ArrayList<>(byOp.values()); }
        public synchronized void save(TxRecord r) { byOp.put(r.operationKey, r); try { m.writerWithDefaultPrettyPrinter().writeValue(file, byOp); } catch (Exception e) { throw new RuntimeException(e); } }
        public synchronized long nextNonceFloor(String signer) { return byOp.values().stream().filter(r -> r.signer.equalsIgnoreCase(signer)).mapToLong(r -> r.nonce + 1).max().orElse(0); }
    }

    public static final class Tracker {
        private final Web3j web3j; private final Credentials creds; private final long chainId; private final Env env; private final Ledger ledger;
        private final LongSupplier finalizedBlock; private final BigInteger prio, maxFee; private final long nullGraceMs;
        public final List<String> hintsSeen = new ArrayList<>();

        public Tracker(Web3j web3j, Credentials creds, long chainId, Env env, Ledger ledger, LongSupplier finalizedBlock, BigInteger prio, BigInteger maxFee, long nullGraceMs) {
            this.web3j = web3j; this.creds = creds; this.chainId = chainId; this.env = env; this.ledger = ledger; this.finalizedBlock = finalizedBlock;
            this.prio = prio; this.maxFee = maxFee; this.nullGraceMs = nullGraceMs;
        }

        /** Business-level idempotency: the same operation key NEVER creates a second transaction. */
        public TxRecord execute(String operationKey, String to, String data, long gas) throws Exception {
            TxRecord existing = ledger.get(operationKey);
            if (existing != null && existing.state != TxState.CREATED) return existing;              // already submitted/unknown/confirmed: return, do not resend with a new nonce
            TxRecord r = existing;
            if (r == null) {
                r = new TxRecord(); r.operationKey = operationKey; r.environment = env.name(); r.chainId = chainId; r.signer = creds.getAddress();
                long node = web3j.ethGetTransactionCount(creds.getAddress(), DefaultBlockParameterName.PENDING).send().getTransactionCount().longValue();
                r.nonce = Math.max(node, ledger.nextNonceFloor(creds.getAddress()));
                RawTransaction tx = RawTransaction.createTransaction(chainId, BigInteger.valueOf(r.nonce), BigInteger.valueOf(gas), to, BigInteger.ZERO, data, prio, maxFee);
                byte[] signed = TransactionEncoder.signMessage(tx, chainId, creds);
                r.rawTx = Numeric.toHexString(signed); r.txHash = Numeric.toHexString(Hash.sha3(signed));
                r.history.add(Instant.now() + " created nonce=" + r.nonce + " hash=" + r.txHash);
                ledger.save(r);                                                                       // persisted BEFORE sending
            }
            return submit(r);
        }

        TxRecord submit(TxRecord r) {
            r.sends++;
            try {
                EthSendTransaction resp = web3j.ethSendRawTransaction(r.rawTx).send();
                if (!resp.hasError()) { r.to(TxState.SUBMITTED, "node returned hash (acceptance != success)"); }
                else {
                    String msg = String.valueOf(resp.getError().getMessage()).toLowerCase(); r.lastError = msg;
                    if (msg.contains("already") || msg.contains("known") || msg.contains("imported")) r.to(TxState.SUBMITTED, "already known: idempotent resubmission");
                    else if (msg.contains("nonce too low")) r.to(TxState.UNKNOWN, "nonce consumed: ours or another; reconcile by hash");
                    else r.to(TxState.CREATED, "rejected at admission (not on chain): " + msg);        // underpriced / insufficient funds / invalid sender
                }
            } catch (Exception transport) { r.lastError = transport.getClass().getSimpleName(); r.to(TxState.UNKNOWN, "transport failure after/before send: outcome unknown"); }
            ledger.save(r); return r;
        }

        /** Idempotent, restart-safe reconciliation by tx hash and nonce. Never infers failure from null/errors. */
        public TxRecord reconcile(TxRecord r) {
            if (!r.environment.equals(env.name()) || r.chainId != chainId) return r;                  // record isolation
            if (r.state == TxState.FINALIZED || r.state == TxState.REVERTED || r.state == TxState.REPLACED || r.state == TxState.CREATED) return r;
            try {
                Optional<TransactionReceipt> rc = web3j.ethGetTransactionReceipt(r.txHash).send().getTransactionReceipt();
                if (rc.isPresent()) {
                    r.blockNumber = rc.get().getBlockNumber().longValue();
                    if (r.state != TxState.CONFIRMED) {
                        if ("0x0".equals(rc.get().getStatus())) { r.to(TxState.REVERTED, "receipt status 0 (executed and reverted)"); ledger.save(r); return r; }
                        r.to(TxState.CONFIRMED, "receipt status 1 at block " + r.blockNumber);
                    }
                    if (finalizedBlock.getAsLong() >= r.blockNumber) r.to(TxState.FINALIZED, "finality policy satisfied");
                    r.firstNullAtMs = 0; ledger.save(r); return r;
                }
                boolean byHash = web3j.ethGetTransactionByHash(r.txHash).send().getTransaction().isPresent();
                if (byHash) { if (r.state != TxState.PENDING) r.to(TxState.PENDING, "known to node, no receipt yet"); r.firstNullAtMs = 0; ledger.save(r); return r; }
                // both null: NOT proof of absence
                long now = System.currentTimeMillis(); if (r.firstNullAtMs == 0) r.firstNullAtMs = now;
                if (r.state != TxState.UNKNOWN) r.to(TxState.UNKNOWN, "no receipt and not found by hash (provider may lag)");
                if (now - r.firstNullAtMs >= nullGraceMs) {
                    long latest = web3j.ethGetTransactionCount(r.signer, DefaultBlockParameterName.LATEST).send().getTransactionCount().longValue();
                    if (latest > r.nonce) r.to(TxState.REPLACED, "nonce " + r.nonce + " consumed (latest=" + latest + ") by a different transaction; our hash has no receipt");
                    else { r.rebroadcasts++; web3j.ethSendRawTransaction(r.rawTx).send(); r.history.add(Instant.now() + " rebroadcast same raw tx (same hash)"); }
                }
            } catch (Exception transport) { r.lastError = transport.getClass().getSimpleName(); /* keep state: a failed lookup is not evidence */ }
            ledger.save(r); return r;
        }

        public void reconcileAll() { for (TxRecord r : ledger.all()) reconcile(r); }

        /** WebSocket / newAcceptedTransactions events are latency hints only: they trigger a reconcile, never set state themselves. */
        public void onHint(String txHash) {
            hintsSeen.add(txHash);
            ledger.all().stream().filter(r -> txHash.equalsIgnoreCase(r.txHash)).forEach(this::reconcile);
        }
    }
}
