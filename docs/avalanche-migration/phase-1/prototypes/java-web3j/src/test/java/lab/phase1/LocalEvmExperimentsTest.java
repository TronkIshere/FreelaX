package lab.phase1;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;
import tools.jackson.databind.JsonNode;

import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static lab.phase1.Evidence.of;
import static lab.phase1.Evidence.rec;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 experiments against a LOCAL anvil (docker, chain 31337). Requires: docker anvil on :8545 (scripts/start-anvil.sh).
 * Ephemeral keys only. Nothing here touches Fuji or Mainnet.
 */
@SpringBootTest(properties = "phase1.rpc-url=http://localhost:8545")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LocalEvmExperimentsTest {
    static final String RPC = "http://localhost:8545";
    static final long CHAIN = 31337;
    static final Rpc raw = new Rpc(RPC, Duration.ofSeconds(10));

    @Autowired Web3j web3j;

    static Credentials admin, user, authSigner;
    static String lab;

    static final BigInteger PRIO = BigInteger.valueOf(1_000_000_000L);
    static final BigInteger MAXFEE = BigInteger.valueOf(20_000_000_000L);

    // ---------- helpers ----------
    static Credentials fresh() throws Exception { return Credentials.create(Keys.createEcKeyPair()); }

    static void fund(String addr, long avax) throws Exception {
        raw.result("anvil_setBalance", addr, Numeric.toHexStringWithPrefix(BigInteger.valueOf(avax).multiply(BigInteger.TEN.pow(18))));
    }

    record Sent(String localHash, EthSendTransaction resp, String rawHex) {}

    Sent send(Credentials c, String to, String data, BigInteger nonce, long gas, BigInteger prio, BigInteger maxFee) throws Exception {
        RawTransaction tx = RawTransaction.createTransaction(CHAIN, nonce, BigInteger.valueOf(gas), to == null ? "" : to, BigInteger.ZERO, data, prio, maxFee);
        byte[] signed = TransactionEncoder.signMessage(tx, CHAIN, c);
        String hex = Numeric.toHexString(signed);
        String localHash = Numeric.toHexString(Hash.sha3(signed));          // hash is knowable BEFORE submission
        return new Sent(localHash, web3j.ethSendRawTransaction(hex).send(), hex);
    }

    Sent send(Credentials c, String to, String data, long gas) throws Exception {
        BigInteger n = web3j.ethGetTransactionCount(c.getAddress(), DefaultBlockParameterName.PENDING).send().getTransactionCount();
        return send(c, to, data, n, gas, PRIO, MAXFEE);
    }

    TransactionReceipt waitReceipt(String hash, int seconds) throws Exception {
        long end = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < end) {
            Optional<TransactionReceipt> r = web3j.ethGetTransactionReceipt(hash).send().getTransactionReceipt();
            if (r.isPresent()) return r.get();
            Thread.sleep(200);
        }
        return null;
    }

    static String enc(String fn, Type<?>... args) { return FunctionEncoder.encode(new Function(fn, Arrays.asList(args), List.of())); }

    /** A call any account may make (no role needed) that always succeeds on-chain. */
    static String okCall(long x) { return enc("actUntil", new Uint256(BigInteger.TWO.pow(60).add(BigInteger.valueOf(x)))); }

    static String sel(String sig) { return Numeric.toHexString(Hash.sha3(sig.getBytes(StandardCharsets.UTF_8))).substring(0, 10); }

    /** eth_call and return {ok, data|errorData}. */
    String callRevertData(Credentials from, String to, String data) throws Exception {
        JsonNode r = raw.call("eth_call", Map.of("from", from.getAddress(), "to", to, "data", data), "latest");
        if (r.has("error")) { JsonNode d = r.get("error").get("data"); return d == null ? "ERR:" + r.get("error").get("message").asText() : d.asText(); }
        return "OK:" + r.get("result").asText();
    }

    static String bytesHex(byte[] b) { return Numeric.toHexString(b); }

    static Bytes32 b32(byte[] b) { return new Bytes32(b); }

    // ---------- 1-3: startup, connectivity, deploy ----------
    @Test @Order(1)
    void springBootStartsAndConnects() throws Exception {
        assertNotNull(web3j);
        BigInteger chain = web3j.ethChainId().send().getChainId();
        EthBlock.Block b = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock();
        assertEquals(CHAIN, chain.longValue());
        admin = fresh(); user = fresh(); authSigner = fresh();
        fund(admin.getAddress(), 100); fund(user.getAddress(), 100); fund(authSigner.getAddress(), 1);
        BigInteger bal = web3j.ethGetBalance(admin.getAddress(), DefaultBlockParameterName.LATEST).send().getBalance();
        rec("J1-spring-web3j-connect", "PASS", of("chainId", chain, "head", b.getNumber(), "headTs", b.getTimestamp(), "balanceWei", bal,
                "java", System.getProperty("java.version"), "springBoot", org.springframework.boot.SpringBootVersion.getVersion()));
    }

    @Test @Order(2)
    void deployLabAndGrantRoles() throws Exception {
        String bin = new String(Objects.requireNonNull(getClass().getResourceAsStream("/Phase1Lab.bin")).readAllBytes()).trim();
        String ctor = FunctionEncoder.encodeConstructor(List.of(new Address(admin.getAddress())));
        Sent s = send(admin, null, bin + Numeric.cleanHexPrefix(ctor), web3j.ethGetTransactionCount(admin.getAddress(), DefaultBlockParameterName.PENDING).send().getTransactionCount(), 3_000_000, PRIO, MAXFEE);
        assertFalse(s.resp.hasError(), () -> String.valueOf(s.resp.getError()));
        assertEquals(s.localHash, s.resp.getTransactionHash(), "locally computed hash must equal node hash");
        TransactionReceipt r = waitReceipt(s.localHash, 15);
        assertNotNull(r); assertTrue(r.isStatusOK());
        lab = r.getContractAddress();
        // roles
        for (String[] g : new String[][]{{"OPERATOR_ROLE", admin.getAddress()}, {"AUTH_SIGNER_ROLE", authSigner.getAddress()}}) {
            Sent gs = send(admin, lab, enc("grantRole", b32(Hash.sha3(g[0].getBytes(StandardCharsets.UTF_8))), new Address(g[1])), 200_000);
            assertTrue(waitReceipt(gs.localHash, 15).isStatusOK());
        }
        String code = web3j.ethGetCode(lab, DefaultBlockParameterName.LATEST).send().getCode();
        rec("J2-deploy-and-roles", "PASS", of("contract", lab, "deployTx", s.localHash, "gasUsed", r.getGasUsed(), "codeBytes", (code.length() - 2) / 2,
                "localHashEqualsNodeHash", true));
    }

    // ---------- 4-5: success, event decoding, revert ----------
    static final Event VALUE_SET = new Event("ValueSet", List.of(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));

    @Test @Order(3)
    void successfulTxAndEventDecode() throws Exception {
        Sent s = send(admin, lab, enc("setValue", new Uint256(7)), 200_000);
        TransactionReceipt r = waitReceipt(s.localHash, 15);
        assertTrue(r.isStatusOK());
        Log log = r.getLogs().get(0);
        assertEquals(lab.toLowerCase(), log.getAddress().toLowerCase());
        assertEquals(EventEncoder.encode(VALUE_SET), log.getTopics().get(0));
        String by = FunctionReturnDecoder.decodeIndexedValue(log.getTopics().get(1), new TypeReference<Address>() {}).getValue().toString();
        List<Type> nonIdx = FunctionReturnDecoder.decode(log.getData(), VALUE_SET.getNonIndexedParameters());
        assertEquals(admin.getAddress().toLowerCase(), by.toLowerCase());
        assertEquals(BigInteger.valueOf(7), nonIdx.get(1).getValue());
        // read back through eth_call + ABI decode
        String out = web3j.ethCall(Transaction.createEthCallTransaction(admin.getAddress(), lab, FunctionEncoder.encode(new Function("value", List.of(), List.of(new TypeReference<Uint256>() {})))), DefaultBlockParameterName.LATEST).send().getValue();
        List<Type> dec = FunctionReturnDecoder.decode(out, List.of(new TypeReference<Uint256>() {}).stream().map(t -> (TypeReference<Type>) (TypeReference) t).toList());
        assertEquals(BigInteger.valueOf(7), dec.get(0).getValue());
        rec("J3-success-event-decode", "PASS", of("tx", s.localHash, "status", r.getStatus(), "logIndex", log.getLogIndex(), "decodedNewValue", nonIdx.get(1).getValue(), "gasUsed", r.getGasUsed()));
    }

    @Test @Order(4)
    void revertedTxHasReceiptStatus0AndDecodableReason() throws Exception {
        // eth_call shows the revert (custom error Boom(42))
        String data = enc("boom", new Uint256(42));
        String rd = callRevertData(user, lab, data);
        assertTrue(rd.startsWith(sel("Boom(uint256)")), rd);
        // eth_estimateGas also refuses
        EthEstimateGas eg = web3j.ethEstimateGas(Transaction.createFunctionCallTransaction(user.getAddress(), null, null, null, lab, data)).send();
        assertTrue(eg.hasError());
        // force inclusion with explicit gas limit => mined revert
        Sent s = send(user, lab, data, 100_000);
        assertFalse(s.resp.hasError());                                   // ACCEPTED by node != success
        TransactionReceipt r = waitReceipt(s.localHash, 15);
        assertNotNull(r);
        assertFalse(r.isStatusOK());
        assertEquals("0x0", r.getStatus());
        EthTransaction t = web3j.ethGetTransactionByHash(s.localHash).send();
        assertTrue(t.getTransaction().isPresent());
        // access control revert
        String ac = callRevertData(user, lab, enc("setValue", new Uint256(1)));
        assertTrue(ac.startsWith(sel("AccessControlUnauthorizedAccount(address,bytes32)")), ac);
        rec("J4-revert-detection", "PASS", of("tx", s.localHash, "submitAccepted", true, "receiptStatus", r.getStatus(), "logsOnRevert", r.getLogs().size(),
                "ethCallRevertData", rd.substring(0, 10) + "...", "estimateGasError", eg.getError().getMessage(), "accessControlSelectorMatch", true));
    }

    // ---------- 6-9: nonces, funds, duplicate, replacement, pending ----------
    @Test @Order(5)
    void nonceTooLowInsufficientFundsDuplicate() throws Exception {
        Credentials c = fresh(); fund(c.getAddress(), 10);
        // good tx nonce 0
        Sent ok = send(c, lab, okCall(1), BigInteger.ZERO, 100_000, PRIO, MAXFEE);
        assertTrue(waitReceipt(ok.localHash, 15) != null);
        // nonce too low (reuse nonce 0 with different payload)
        Sent low = send(c, lab, okCall(2), BigInteger.ZERO, 100_000, PRIO, MAXFEE);
        assertTrue(low.resp.hasError());
        String lowMsg = low.resp.getError().getMessage();
        // duplicate submission of the SAME signed tx after it mined
        EthSendTransaction dup = web3j.ethSendRawTransaction(ok.rawHex).send();
        assertTrue(dup.hasError());
        String dupMsg = dup.getError().getMessage();
        // insufficient funds (zero balance account)
        Credentials poor = fresh();
        Sent poorTx = send(poor, lab, okCall(1), BigInteger.ZERO, 100_000, PRIO, MAXFEE);
        assertTrue(poorTx.resp.hasError());
        // duplicate of a still-pending tx (automine off)
        raw.result("evm_setAutomine", false);
        try {
            Sent p = send(c, lab, okCall(3), BigInteger.ONE, 100_000, PRIO, MAXFEE);
            assertFalse(p.resp.hasError());
            EthSendTransaction dupPending = web3j.ethSendRawTransaction(p.rawHex).send();
            String dpMsg = dupPending.hasError() ? dupPending.getError().getMessage() : "NO ERROR (idempotent resubmit, hash=" + dupPending.getTransactionHash() + ")";
            // pending visibility: found by hash but no block, no receipt
            EthTransaction byHash = web3j.ethGetTransactionByHash(p.localHash).send();
            boolean foundPending = byHash.getTransaction().isPresent();
            String blk = foundPending ? String.valueOf(byHash.getTransaction().get().getBlockNumberRaw()) : "n/a";
            boolean receiptPresent = web3j.ethGetTransactionReceipt(p.localHash).send().getTransactionReceipt().isPresent();
            long latestNonce = web3j.ethGetTransactionCount(c.getAddress(), DefaultBlockParameterName.LATEST).send().getTransactionCount().longValue();
            long pendingNonce = web3j.ethGetTransactionCount(c.getAddress(), DefaultBlockParameterName.PENDING).send().getTransactionCount().longValue();
            raw.result("evm_mine");
            TransactionReceipt after = waitReceipt(p.localHash, 5);
            rec("J5-nonce-funds-duplicate-pending", "PASS", of("nonceTooLow", lowMsg, "duplicateAfterMined", dupMsg, "insufficientFunds", poorTx.resp.getError().getMessage(),
                    "duplicateWhilePending", dpMsg, "pendingTxFoundByHash", foundPending, "pendingTxBlockNumber", blk, "pendingTxReceiptPresent", receiptPresent,
                    "latestNonceWhilePending", latestNonce, "pendingNonceWhilePending", pendingNonce, "receiptAfterMine", after != null,
                    "NOTE", "anvil local pool; NOT Helicon accepted-vs-executed semantics"));
            assertTrue(foundPending); assertFalse(receiptPresent); assertNotNull(after);
        } finally { raw.result("evm_setAutomine", true); }
    }

    @Test @Order(6)
    void nonceGapAndReplacement() throws Exception {
        Credentials c = fresh(); fund(c.getAddress(), 10);
        // nonce gap: send nonce 1 while 0 unsent
        Sent gap = send(c, lab, okCall(11), BigInteger.ONE, 100_000, PRIO, MAXFEE);
        boolean gapAccepted = !gap.resp.hasError();
        boolean gapFound = web3j.ethGetTransactionByHash(gap.localHash).send().getTransaction().isPresent();
        // replacement of the gap tx: same nonce, same fee => underpriced; +50% => accepted
        Sent same = send(c, lab, okCall(12), BigInteger.ONE, 100_000, PRIO, MAXFEE);
        Sent bumped = send(c, lab, okCall(13), BigInteger.ONE, 100_000, PRIO.multiply(BigInteger.valueOf(3)), MAXFEE.multiply(BigInteger.valueOf(3)));
        boolean origStillPresent = web3j.ethGetTransactionByHash(gap.localHash).send().getTransaction().isPresent();
        // fill gap -> both nonces should mine; replaced tx must never get a receipt
        Sent n0 = send(c, lab, okCall(10), BigInteger.ZERO, 100_000, PRIO, MAXFEE);
        TransactionReceipt r0 = waitReceipt(n0.localHash, 15);
        TransactionReceipt rb = bumped.resp.hasError() ? null : waitReceipt(bumped.localHash, 15);
        TransactionReceipt ro = web3j.ethGetTransactionReceipt(gap.localHash).send().getTransactionReceipt().orElse(null);
        long latest = web3j.ethGetTransactionCount(c.getAddress(), DefaultBlockParameterName.LATEST).send().getTransactionCount().longValue();
        rec("J6-nonce-gap-and-replacement", gapAccepted ? "PASS" : "INCONCLUSIVE", of(
                "gapTxAccepted", gapAccepted, "gapTxFoundByHash", gapFound,
                "sameFeeReplacement", same.resp.hasError() ? same.resp.getError().getMessage() : "ACCEPTED",
                "bumpedReplacement", bumped.resp.hasError() ? bumped.resp.getError().getMessage() : "ACCEPTED",
                "originalVisibleAfterReplacement", origStillPresent, "gapFilledReceipt", r0 != null, "replacementMined", rb != null, "replacementStatus", rb == null ? "none" : rb.getStatus(),
                "replacedOriginalReceipt", ro != null, "finalLatestNonce", latest));
        assertNotNull(r0);
        assertNull(ro, "a replaced transaction must never get a receipt");
    }

    // ---------- 10: timeout after submission, temporary RPC failure ----------
    @Test @Order(7)
    void timeoutAfterSubmissionAndTemporaryFailure() throws Exception {
        AtomicInteger failNext = new AtomicInteger(0);
        AtomicInteger dropSendResponse = new AtomicInteger(0);
        HttpServer proxy = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        HttpClient fwd = HttpClient.newHttpClient();
        proxy.createContext("/", ex -> {
            try {
                byte[] body = ex.getRequestBody().readAllBytes();
                if (failNext.get() > 0) { failNext.decrementAndGet(); ex.sendResponseHeaders(503, -1); ex.close(); return; }
                HttpResponse<byte[]> up = fwd.send(HttpRequest.newBuilder(URI.create(RPC)).header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
                if (new String(body).contains("eth_sendRawTransaction") && dropSendResponse.get() > 0) { dropSendResponse.decrementAndGet(); Thread.sleep(4000); }
                ex.sendResponseHeaders(200, up.body().length); ex.getResponseBody().write(up.body()); ex.close();
            } catch (Exception e) { try { ex.close(); } catch (Exception ignore) {} }
        });
        proxy.start();
        String proxyUrl = "http://127.0.0.1:" + proxy.getAddress().getPort();
        okhttp3.OkHttpClient shortClient = new okhttp3.OkHttpClient.Builder().readTimeout(Duration.ofSeconds(2)).callTimeout(Duration.ofSeconds(2)).build();
        Web3j flaky = Web3j.build(new HttpService(proxyUrl, shortClient));
        try {
            Credentials c = fresh(); fund(c.getAddress(), 10);
            RawTransaction tx = RawTransaction.createTransaction(CHAIN, BigInteger.ZERO, BigInteger.valueOf(100_000), lab, BigInteger.ZERO, okCall(99), PRIO, MAXFEE);
            byte[] signed = TransactionEncoder.signMessage(tx, CHAIN, c);
            String hash = Numeric.toHexString(Hash.sha3(signed));          // persisted BEFORE send in a real design
            dropSendResponse.set(1);
            String sendOutcome;
            try { flaky.ethSendRawTransaction(Numeric.toHexString(signed)).send(); sendOutcome = "unexpected success"; }
            catch (Exception e) { sendOutcome = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()); }
            // outcome unknown to the client; reconcile by hash through a healthy path
            TransactionReceipt r = waitReceipt(hash, 15);
            boolean minedDespiteTimeout = r != null && r.isStatusOK();
            // safe re-broadcast of the same raw tx is idempotent-ish: must error (already known/nonce too low), never double-execute
            EthSendTransaction again = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
            long nonceAfter = web3j.ethGetTransactionCount(c.getAddress(), DefaultBlockParameterName.LATEST).send().getTransactionCount().longValue();
            // temporary failure: 503 x2 then recovery
            failNext.set(2);
            List<String> seq = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                try { seq.add("ok:" + flaky.ethBlockNumber().send().getBlockNumber()); } catch (Exception e) { seq.add(e.getClass().getSimpleName()); }
            }
            rec("J7-timeout-after-submit-and-rpc-recovery", "PASS", of("clientSendOutcome", sendOutcome, "txMinedDespiteClientTimeout", minedDespiteTimeout,
                    "lookupByLocallyComputedHash", true, "rebroadcastAfterMined", again.hasError() ? again.getError().getMessage() : "ACCEPTED", "nonceAfter", nonceAfter,
                    "temporaryFailureSequence", seq));
            assertTrue(minedDespiteTimeout);
            assertEquals(1, nonceAfter);
        } finally { flaky.shutdown(); proxy.stop(0); }
    }

    // ---------- timestamp / deadline on a real block ----------
    static final Event ACTED = new Event("Acted", List.of(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));

    @Test @Order(8)
    void deadlineBoundariesOnRealBlocks() throws Exception {
        long base = Long.parseLong(raw.result("eth_getBlockByNumber", "latest", false).get("timestamp").asText().substring(2), 16) + 1000;
        Map<String, Object> out = new LinkedHashMap<>();
        // case: block.timestamp == deadline
        long d = base;
        raw.result("evm_setNextBlockTimestamp", d);
        Sent eq = send(user, lab, enc("actUntil", new Uint256(d)), 100_000);
        TransactionReceipt r1 = waitReceipt(eq.localHash, 10);
        out.put("actUntil(ts==deadline)", r1.isStatusOK() ? "SUCCESS" : "REVERT");
        long ts1 = Long.parseLong(raw.result("eth_getBlockByNumber", r1.getBlockNumber().toString(), false).get("timestamp").asText().substring(2), 16);
        out.put("blockTimestampEqualsDeadline", ts1 == d);
        // case: actAfter at ts == deadline must revert
        long d2 = base + 100;
        raw.result("evm_setNextBlockTimestamp", d2);
        Sent af = send(user, lab, enc("actAfter", new Uint256(d2)), 100_000);
        out.put("actAfter(ts==deadline)", waitReceipt(af.localHash, 10).isStatusOK() ? "SUCCESS" : "REVERT");
        // case: ts == deadline+1
        long d3 = base + 200;
        raw.result("evm_setNextBlockTimestamp", d3 + 1);
        Sent late = send(user, lab, enc("actUntil", new Uint256(d3)), 100_000);
        out.put("actUntil(ts==deadline+1)", waitReceipt(late.localHash, 10).isStatusOK() ? "SUCCESS" : "REVERT");
        raw.result("evm_setNextBlockTimestamp", d3 + 2);
        Sent aft = send(user, lab, enc("actAfter", new Uint256(d3)), 100_000);
        out.put("actAfter(ts==deadline+2)", waitReceipt(aft.localHash, 10).isStatusOK() ? "SUCCESS" : "REVERT");
        assertEquals("SUCCESS", out.get("actUntil(ts==deadline)"));
        assertEquals("REVERT", out.get("actAfter(ts==deadline)"));
        assertEquals("REVERT", out.get("actUntil(ts==deadline+1)"));
        assertEquals("SUCCESS", out.get("actAfter(ts==deadline+2)"));
        rec("J8-deadline-boundaries-local-evm", "PASS", new LinkedHashMap<>(out) {{ put("NOTE", "anvil lets the test choose block.timestamp; proves contract comparison semantics only, not Fuji timestamp behaviour"); }});
    }

    // ---------- EIP-712 (Java <-> contract) ----------
    static String typedJson(long chainId, String verifying, String account, byte[] action, BigInteger nonce, BigInteger deadline) {
        return """
            {"types":{"EIP712Domain":[{"name":"name","type":"string"},{"name":"version","type":"string"},{"name":"chainId","type":"uint256"},{"name":"verifyingContract","type":"address"}],
            "Authorization":[{"name":"account","type":"address"},{"name":"action","type":"bytes32"},{"name":"nonce","type":"uint256"},{"name":"deadline","type":"uint256"}]},
            "primaryType":"Authorization",
            "domain":{"name":"FreelaXPhase1Lab","version":"1","chainId":%d,"verifyingContract":"%s"},
            "message":{"account":"%s","action":"%s","nonce":"%s","deadline":"%s"}}
            """.formatted(chainId, verifying, account, Numeric.toHexString(action), nonce, deadline);
    }

    static byte[] sign(ECKeyPair kp, byte[] digest) {
        Sign.SignatureData sd = Sign.signMessage(digest, kp, false);
        byte[] sig = new byte[65];
        System.arraycopy(sd.getR(), 0, sig, 0, 32); System.arraycopy(sd.getS(), 0, sig, 32, 32); sig[64] = sd.getV()[0];
        return sig;
    }

    static String recover(byte[] digest, byte[] sig) throws Exception {
        Sign.SignatureData sd = new Sign.SignatureData(sig[64], Arrays.copyOfRange(sig, 0, 32), Arrays.copyOfRange(sig, 32, 64));
        return "0x" + Keys.getAddress(Sign.signedMessageHashToKey(digest, sd));
    }

    @Test @Order(9)
    void eip712JavaMatchesContractAndNegativeCases() throws Exception {
        byte[] action = Hash.sha3("FUND".getBytes(StandardCharsets.UTF_8));
        BigInteger dl = BigInteger.valueOf(System.currentTimeMillis() / 1000 + 3600);
        BigInteger nonce = BigInteger.valueOf(1001);
        byte[] digest = new StructuredDataEncoder(typedJson(CHAIN, lab, user.getAddress(), action, nonce, dl)).hashStructuredData();
        // 1. Java digest == contract digest
        String onchain = web3j.ethCall(Transaction.createEthCallTransaction(user.getAddress(), lab,
                FunctionEncoder.encode(new Function("authDigest", List.of(new Address(user.getAddress()), b32(action), new Uint256(nonce), new Uint256(dl)), List.of(new TypeReference<Bytes32>() {})))), DefaultBlockParameterName.LATEST).send().getValue();
        assertEquals(bytesHex(digest), onchain, "Java StructuredDataEncoder digest must equal contract _hashTypedDataV4");
        // 2. sign, recover in Java
        byte[] sig = sign(authSigner.getEcKeyPair(), digest);
        assertEquals(authSigner.getAddress().toLowerCase(), recover(digest, sig).toLowerCase());
        // 3. invalid signature recovers a different address
        byte[] tampered = sig.clone(); tampered[10] ^= 0x01;
        String tamperedRecovered; try { tamperedRecovered = recover(digest, tampered); } catch (Exception e) { tamperedRecovered = "recover-error:" + e.getClass().getSimpleName(); }
        assertNotEquals(authSigner.getAddress().toLowerCase(), tamperedRecovered.toLowerCase());
        // 4. submit to contract: success
        String call = enc("useAuthorization", b32(action), new Uint256(nonce), new Uint256(dl), new DynamicBytes(sig));
        Sent ok = send(user, lab, call, 200_000);
        assertTrue(waitReceipt(ok.localHash, 15).isStatusOK());
        // 5. replay -> NonceUsed
        String replay = callRevertData(user, lab, call);
        assertTrue(replay.startsWith(sel("NonceUsed()")), replay);
        // 6. wrong chain id in domain -> BadSigner
        byte[] wrongChainDigest = new StructuredDataEncoder(typedJson(43114, lab, user.getAddress(), action, BigInteger.valueOf(1002), dl)).hashStructuredData();
        String wc = callRevertData(user, lab, enc("useAuthorization", b32(action), new Uint256(1002), new Uint256(dl), new DynamicBytes(sign(authSigner.getEcKeyPair(), wrongChainDigest))));
        assertTrue(wc.startsWith(sel("BadSigner()")), wc);
        // 7. wrong verifying contract
        byte[] wvDigest = new StructuredDataEncoder(typedJson(CHAIN, "0x000000000000000000000000000000000000dEaD", user.getAddress(), action, BigInteger.valueOf(1003), dl)).hashStructuredData();
        String wv = callRevertData(user, lab, enc("useAuthorization", b32(action), new Uint256(1003), new Uint256(dl), new DynamicBytes(sign(authSigner.getEcKeyPair(), wvDigest))));
        assertTrue(wv.startsWith(sel("BadSigner()")), wv);
        // 8. expired
        BigInteger past = BigInteger.valueOf(System.currentTimeMillis() / 1000 - 60);
        byte[] expDigest = new StructuredDataEncoder(typedJson(CHAIN, lab, user.getAddress(), action, BigInteger.valueOf(1004), past)).hashStructuredData();
        String ex = callRevertData(user, lab, enc("useAuthorization", b32(action), new Uint256(1004), new Uint256(past), new DynamicBytes(sign(authSigner.getEcKeyPair(), expDigest))));
        assertTrue(ex.startsWith(sel("Expired()")), ex);
        // 9. signer without role (random key)
        Credentials rogue = fresh();
        byte[] rd = new StructuredDataEncoder(typedJson(CHAIN, lab, user.getAddress(), action, BigInteger.valueOf(1005), dl)).hashStructuredData();
        String rg = callRevertData(user, lab, enc("useAuthorization", b32(action), new Uint256(1005), new Uint256(dl), new DynamicBytes(sign(rogue.getEcKeyPair(), rd))));
        assertTrue(rg.startsWith(sel("BadSigner()")), rg);
        // deterministic signature (for cross-implementation comparison with cast)
        String typed = typedJson(CHAIN, lab, user.getAddress(), action, nonce, dl).replace("\n", "");
        // public vector (NO key) goes to evidence; the valueless ephemeral key goes ONLY to the OS temp dir for the cast interop script
        Files.writeString(Path.of(System.getProperty("evidence.dir", "../../evidence"), "eip712-java-vector.json"),
                "{\"digest\":\"" + bytesHex(digest) + "\",\"signature\":\"" + bytesHex(sig) + "\",\"signer\":\"" + authSigner.getAddress() + "\",\"typedData\":" + typed + "}");
        Files.writeString(Path.of(System.getProperty("java.io.tmpdir"), "phase1-eip712-key.txt"), Numeric.toHexStringNoPrefix(authSigner.getEcKeyPair().getPrivateKey()));
        Files.writeString(Path.of(System.getProperty("java.io.tmpdir"), "phase1-eip712-typed.json"), typed);
        rec("J9-eip712-java-vs-contract", "PASS", of("digestMatchesContract", true, "recoveredSigner", true, "tamperedRecovered", tamperedRecovered, "replay", "NonceUsed",
                "wrongChainId", "BadSigner", "wrongVerifyingContract", "BadSigner", "expired", "Expired", "roguelessSigner", "BadSigner", "libs", "web3j StructuredDataEncoder + Sign (secp256k1, low-s)"));
    }

    // ---------- nonce management illustration ----------
    @Test @Order(10)
    void naiveNonceFetchRaceIsReal() throws Exception {
        Credentials c = fresh(); fund(c.getAddress(), 10);
        BigInteger n1 = web3j.ethGetTransactionCount(c.getAddress(), DefaultBlockParameterName.PENDING).send().getTransactionCount();
        BigInteger n2 = web3j.ethGetTransactionCount(c.getAddress(), DefaultBlockParameterName.PENDING).send().getTransactionCount();   // second worker reads before first sends
        Sent a = send(c, lab, okCall(21), n1, 100_000, PRIO, MAXFEE);
        waitReceipt(a.localHash, 10);
        Sent b = send(c, lab, okCall(22), n2, 100_000, PRIO, MAXFEE);
        rec("J10-naive-nonce-race", "PASS", of("sameNonceReadTwice", n1.equals(n2), "secondSend", b.resp.hasError() ? b.resp.getError().getMessage() : "ACCEPTED",
                "implication", "Two workers reading the node nonce concurrently collide; a DB-backed per-signer allocator is required (Phase 2)"));
        assertTrue(b.resp.hasError());
    }

    @AfterAll
    static void flush() { Evidence.flush("java-local-experiments.json"); }
}
