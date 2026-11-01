package lab.phase1;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import lab.phase1.EnvGuard.Env;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reusable live experiment (Part B task C). DISABLED unless -Dlive.env is set.
 *
 *   generate:  -Dlive.env=AVALANCHE_FUJI -Dlive.mode=generate [-Dlive.keyfile=...]   -> creates an EPHEMERAL testnet-only key, prints ONLY the address
 *   run:       -Dlive.env=AVALANCHE_FUJI|LOCAL_SIMULATION -Dlive.rpc=<url> [-Dlive.keyfile=...]
 *
 * Safety: EnvGuard fails closed (rejects Mainnet chain ids/endpoints, enforces env/endpoint pairing); Fuji signer balance above
 * MAX_BALANCE_AVAX is refused (a faucet wallet never has that much); gas is capped; nothing is printed except addresses/hashes.
 * For LOCAL_SIMULATION without a key file an in-memory key is created and funded via anvil_setBalance.
 * Output: evidence/live/<env>-chain<id>-<utc>.json  (observations only; each entry states what was measured).
 */
@EnabledIfSystemProperty(named = "live.env", matches = ".+")
class LiveExperimentsTest {
    static final BigInteger MAX_BALANCE_WEI = BigInteger.TEN.multiply(BigInteger.TEN.pow(18));      // 10 AVAX
    static final BigInteger MIN_BALANCE_WEI = BigInteger.TEN.pow(16);                                // 0.01 AVAX
    static final Path DEFAULT_KEY = Path.of(System.getProperty("user.home"), ".freelax-phase1", "ephemeral-testnet.key");
    static final String BIN_RESOURCE = "/Phase1Lab.bin";

    final Env env = Env.valueOf(System.getProperty("live.env"));
    final String rpcUrl = System.getProperty("live.rpc", env == Env.AVALANCHE_FUJI ? "https://api.avax-test.network/ext/bc/C/rpc" : "http://localhost:8545");
    final Map<String, Object> out = new LinkedHashMap<>();
    Web3j web3j;
    Rpc raw;
    long chainId;
    Credentials creds;
    BigInteger prio, maxFee;
    String lab;

    // ---------- plumbing ----------
    static Path keyPath() { return Path.of(System.getProperty("live.keyfile", DEFAULT_KEY.toString())); }

    Web3j client(String url) {
        return Web3j.build(new HttpService(url, new okhttp3.OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(10)).readTimeout(Duration.ofSeconds(20)).callTimeout(Duration.ofSeconds(25)).build()));
    }

    void rec(String id, String status, Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status); m.put("at", Instant.now().toString());
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        out.put(id, m);
        System.out.println("[LIVE] " + id + " " + m);
    }

    static String enc(String fn, Type<?>... a) { return FunctionEncoder.encode(new Function(fn, Arrays.asList(a), List.of())); }

    long blockTs(BigInteger number) throws Exception {
        EthBlock.Block b = web3j.ethGetBlockByNumber(org.web3j.protocol.core.DefaultBlockParameter.valueOf(number), false).send().getBlock();
        return b.getTimestamp().longValue();
    }

    record Sent(String hash, String rawHex, EthSendTransaction resp, long submitNanos, long ackNanos) {}

    Sent send(String to, String data, BigInteger nonce, long gas, BigInteger p, BigInteger m) throws Exception {
        RawTransaction tx = RawTransaction.createTransaction(chainId, nonce, BigInteger.valueOf(gas), to == null ? "" : to, BigInteger.ZERO, data, p, m);
        String hex = Numeric.toHexString(TransactionEncoder.signMessage(tx, chainId, creds));
        String hash = Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(hex)));
        long t0 = System.nanoTime();
        EthSendTransaction r = web3j.ethSendRawTransaction(hex).send();
        return new Sent(hash, hex, r, t0, System.nanoTime());
    }

    BigInteger pendingNonce() throws Exception { return web3j.ethGetTransactionCount(creds.getAddress(), DefaultBlockParameterName.PENDING).send().getTransactionCount(); }

    long ms(long fromNanos) { return (System.nanoTime() - fromNanos) / 1_000_000; }

    /** Polls by-hash and receipt every 25 ms; returns timings relative to submit. Finalized tag polled every 250 ms afterwards (<= 40 s). */
    Map<String, Object> observe(Sent s, boolean waitFinalized) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        Long firstByHash = null, firstByHashWithBlock = null, receiptMs = null;
        TransactionReceipt rc = null;
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            var t = web3j.ethGetTransactionByHash(s.hash).send().getTransaction();
            if (t.isPresent()) { if (firstByHash == null) firstByHash = ms(s.submitNanos); if (t.get().getBlockNumberRaw() != null && firstByHashWithBlock == null) firstByHashWithBlock = ms(s.submitNanos); }
            var r = web3j.ethGetTransactionReceipt(s.hash).send().getTransactionReceipt();
            if (r.isPresent()) { rc = r.get(); receiptMs = ms(s.submitNanos); break; }
            Thread.sleep(25);
        }
        m.put("hash", s.hash);
        m.put("ackMs", (s.ackNanos - s.submitNanos) / 1_000_000);
        m.put("firstByHashMs", firstByHash); m.put("firstByHashWithBlockMs", firstByHashWithBlock); m.put("receiptMs", receiptMs);
        if (rc != null) {
            m.put("status", rc.getStatus()); m.put("block", rc.getBlockNumber()); m.put("gasUsed", rc.getGasUsed());
            m.put("blockTimestamp", blockTs(rc.getBlockNumber())); m.put("logs", rc.getLogs().size());
            if (waitFinalized) {
                Long fin = null; long end = System.nanoTime() + Duration.ofSeconds(40).toNanos();
                while (System.nanoTime() < end) {
                    var b = web3j.ethGetBlockByNumber(DefaultBlockParameterName.FINALIZED, false).send().getBlock();
                    if (b != null && b.getNumber().compareTo(rc.getBlockNumber()) >= 0) { fin = ms(s.submitNanos); break; }
                    Thread.sleep(250);
                }
                m.put("finalizedMs", fin);
            }
        }
        return m;
    }

    // ---------- key generation ----------
    @Test
    @EnabledIfSystemProperty(named = "live.mode", matches = "generate")
    void generateEphemeralKey() throws Exception {
        Path p = keyPath();
        if (Files.exists(p)) { System.out.println("[LIVE] key file already exists, address: " + Credentials.create(Files.readString(p).trim()).getAddress()); return; }
        Files.createDirectories(p.getParent());
        ECKeyPair kp = Keys.createEcKeyPair();
        Files.writeString(p, Numeric.toHexStringNoPrefix(kp.getPrivateKey()));
        System.out.println("[LIVE] created EPHEMERAL TESTNET-ONLY key file: " + p + " (never commit it; delete after the experiment)");
        System.out.println("[LIVE] FUND THIS ADDRESS WITH TESTNET AVAX ONLY: " + Credentials.create(kp).getAddress());
    }

    // ---------- the experiment ----------
    @Test
    @EnabledIfSystemProperty(named = "live.mode", matches = "run")
    void runExperiments() throws Exception {
        EnvGuard.checkEndpoint(env, rpcUrl);                                          // before ANY network call
        web3j = client(rpcUrl);
        raw = new Rpc(rpcUrl, Duration.ofSeconds(20));
        chainId = web3j.ethChainId().send().getChainId().longValue();
        EnvGuard.check(env, rpcUrl, chainId);                                         // fail closed
        out.put("environment", env.name()); out.put("chainId", chainId); out.put("endpointHost", URI.create(rpcUrl).getHost());
        out.put("clientVersion", raw.result("web3_clientVersion").asText());
        out.put("startedUtc", Instant.now().toString());

        if (Files.exists(keyPath())) creds = Credentials.create(Files.readString(keyPath()).trim());
        else if (env == Env.LOCAL_SIMULATION) creds = Credentials.create(Keys.createEcKeyPair());
        else throw new IllegalStateException("NO_KEY_FILE: run with -Dlive.mode=generate, fund the printed address with testnet AVAX, then run again");
        out.put("signerAddress", creds.getAddress());
        if (env == Env.LOCAL_SIMULATION) raw.result("anvil_setBalance", creds.getAddress(), "0x" + BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(5)).toString(16));
        BigInteger bal = web3j.ethGetBalance(creds.getAddress(), DefaultBlockParameterName.LATEST).send().getBalance();
        out.put("balanceWeiAtStart", bal);
        if (bal.compareTo(MAX_BALANCE_WEI) > 0) throw new IllegalStateException("REFUSED_SIGNER_BALANCE_TOO_HIGH (possible real-funds wallet)");
        if (bal.compareTo(MIN_BALANCE_WEI) < 0) { rec("E0-preflight", "BLOCKED_NEEDS_FUNDING", "balanceWei", bal, "action", "fund " + creds.getAddress() + " with testnet AVAX"); flush(); return; }

        BigInteger gp = web3j.ethGasPrice().send().getGasPrice();
        prio = gp.max(BigInteger.ONE); maxFee = gp.multiply(BigInteger.valueOf(4)).add(BigInteger.TEN);
        rec("E0-preflight", "OK", "gasPriceWei", gp, "maxPriorityFeeWei", prio, "maxFeeWei", maxFee);

        // E1 deploy
        String bin = new String(Objects.requireNonNull(getClass().getResourceAsStream(BIN_RESOURCE)).readAllBytes(), StandardCharsets.UTF_8).trim();
        String ctor = Numeric.cleanHexPrefix(FunctionEncoder.encodeConstructor(List.of(new Address(creds.getAddress()))));
        Sent d = send(null, bin + ctor, pendingNonce(), 3_000_000, prio, maxFee);
        Map<String, Object> o = observe(d, true);
        TransactionReceipt dr = web3j.ethGetTransactionReceipt(d.hash).send().getTransactionReceipt().orElseThrow();
        lab = dr.getContractAddress();
        o.put("contract", lab);
        rec("E1-deploy-and-timing", "OBSERVED", "obs", o, "rejected", d.resp.hasError() ? d.resp.getError().getMessage() : null);

        // roles
        Sent g = send(lab, enc("grantRole", new Bytes32(Hash.sha3("OPERATOR_ROLE".getBytes(StandardCharsets.UTF_8))), new Address(creds.getAddress())), pendingNonce(), 200_000, prio, maxFee);
        observe(g, false);

        // E2 success + timing
        Sent ok = send(lab, enc("setValue", new Uint256(7)), pendingNonce(), 200_000, prio, maxFee);
        rec("E2-success-timing", "OBSERVED", "obs", observe(ok, true));

        // E3 revert (accepted, executes, status 0)
        Sent bad = send(lab, enc("boom", new Uint256(42)), pendingNonce(), 100_000, prio, maxFee);
        rec("E3-revert", "OBSERVED", "submitAccepted", !bad.resp.hasError(), "obs", observe(bad, false));

        // E4 duplicate submission (before and after mining)
        Sent dup = send(lab, enc("setValue", new Uint256(8)), pendingNonce(), 200_000, prio, maxFee);
        EthSendTransaction again = web3j.ethSendRawTransaction(dup.rawHex).send();
        Map<String, Object> dupObs = observe(dup, false);
        EthSendTransaction late = web3j.ethSendRawTransaction(dup.rawHex).send();
        rec("E4-duplicate-submission", "OBSERVED", "immediateResend", again.hasError() ? again.getError().getMessage() : "ACCEPTED hash=" + again.getTransactionHash(),
                "resendAfterMined", late.hasError() ? late.getError().getMessage() : "ACCEPTED", "obs", dupObs);

        // E5 nonce gap + replacement ladder
        BigInteger n = pendingNonce();
        Sent gap = send(lab, enc("setValue", new Uint256(11)), n.add(BigInteger.ONE), 200_000, prio, maxFee);
        boolean gapAccepted = !gap.resp.hasError();
        boolean gapVisible = web3j.ethGetTransactionByHash(gap.hash).send().getTransaction().isPresent();
        List<Map<String, Object>> ladder = new ArrayList<>();
        Sent accepted = gap;
        for (int pct : new int[]{100, 105, 110, 115, 125, 150, 200}) {
            BigInteger pp = prio.multiply(BigInteger.valueOf(pct)).divide(BigInteger.valueOf(100)).max(BigInteger.ONE);
            BigInteger mm = maxFee.multiply(BigInteger.valueOf(pct)).divide(BigInteger.valueOf(100));
            Sent r = send(lab, enc("setValue", new Uint256(100 + pct)), n.add(BigInteger.ONE), 200_000, pp, mm);
            ladder.add(Map.of("bumpPercent", pct, "result", r.resp.hasError() ? r.resp.getError().getMessage() : "ACCEPTED", "hash", r.hash));
            if (!r.resp.hasError()) { accepted = r; break; }
        }
        Sent fill = send(lab, enc("setValue", new Uint256(10)), n, 200_000, prio, maxFee);
        Map<String, Object> fillObs = observe(fill, false);
        Map<String, Object> repObs = observe(accepted, false);
        boolean originalHasReceipt = !accepted.hash.equals(gap.hash) && web3j.ethGetTransactionReceipt(gap.hash).send().getTransactionReceipt().isPresent();
        boolean originalStillByHash = !accepted.hash.equals(gap.hash) && web3j.ethGetTransactionByHash(gap.hash).send().getTransaction().isPresent();
        rec("E5-nonce-gap-and-replacement", gapAccepted ? "OBSERVED" : "INCONCLUSIVE", "gapAccepted", gapAccepted, "gapVisibleByHash", gapVisible, "ladder", ladder,
                "gapFillerObs", fillObs, "winnerObs", repObs, "replacedOriginalHasReceipt", originalHasReceipt, "replacedOriginalStillByHash", originalStillByHash);

        // E6 timeout after submission (local proxy in front of the endpoint; the proxy holds the response of eth_sendRawTransaction)
        AtomicInteger hold = new AtomicInteger(1);
        HttpServer proxy = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        HttpClient fwd = HttpClient.newHttpClient();
        proxy.createContext("/", ex -> {
            try {
                byte[] body = ex.getRequestBody().readAllBytes();
                HttpResponse<byte[]> up = fwd.send(HttpRequest.newBuilder(URI.create(rpcUrl)).header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
                if (new String(body).contains("eth_sendRawTransaction") && hold.getAndDecrement() > 0) Thread.sleep(4000);
                ex.sendResponseHeaders(200, up.body().length); ex.getResponseBody().write(up.body()); ex.close();
            } catch (Exception e) { try { ex.close(); } catch (Exception ignore) { } }
        });
        proxy.start();
        Web3j flaky = Web3j.build(new HttpService("http://127.0.0.1:" + proxy.getAddress().getPort(), new okhttp3.OkHttpClient.Builder().readTimeout(Duration.ofSeconds(2)).callTimeout(Duration.ofSeconds(2)).build()));
        try {
            BigInteger tn = pendingNonce();
            RawTransaction tx = RawTransaction.createTransaction(chainId, tn, BigInteger.valueOf(200_000), lab, BigInteger.ZERO, enc("setValue", new Uint256(99)), prio, maxFee);
            byte[] signed = TransactionEncoder.signMessage(tx, chainId, creds);
            String hash = Numeric.toHexString(Hash.sha3(signed));                    // persisted before sending in a real design
            String outcome;
            try { flaky.ethSendRawTransaction(Numeric.toHexString(signed)).send(); outcome = "unexpected success"; } catch (Exception e) { outcome = e.getClass().getSimpleName(); }
            Sent s = new Sent(hash, Numeric.toHexString(signed), null, System.nanoTime(), System.nanoTime());
            Map<String, Object> obs = observe(s, false);
            EthSendTransaction resend = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
            long nonceAfter = web3j.ethGetTransactionCount(creds.getAddress(), DefaultBlockParameterName.LATEST).send().getTransactionCount().longValue();
            rec("E6-timeout-after-submission", "OBSERVED", "clientOutcome", outcome, "minedDespiteClientTimeout", obs.get("receiptMs") != null, "obs", obs,
                    "resendAfterMined", resend.hasError() ? resend.getError().getMessage() : "ACCEPTED", "latestNonceAfter", nonceAfter, "expectedNonceAfter", tn.add(BigInteger.ONE));
        } finally { flaky.shutdown(); proxy.stop(0); }

        // E7 near-deadline inclusion: actUntil(d) with d = latestTs + k
        List<Map<String, Object>> dl = new ArrayList<>();
        for (int k : new int[]{0, 1, 2, 3, 5, 8}) {
            long headTs = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getTimestamp().longValue();
            long d1 = headTs + k;
            Sent s = send(lab, enc("actUntil", new Uint256(d1)), pendingNonce(), 100_000, prio, maxFee);
            Map<String, Object> obs = observe(s, false);
            Object bts = obs.get("blockTimestamp");
            boolean consistent = bts != null && (("0x1".equals(obs.get("status"))) == (((Long) bts) <= d1));
            dl.add(new LinkedHashMap<>(Map.of("k", k, "deadline", d1, "headTsAtSubmit", headTs, "includedBlockTs", String.valueOf(bts), "status", String.valueOf(obs.get("status")),
                    "includedMinusDeadline", bts == null ? "n/a" : String.valueOf(((Long) bts) - d1), "statusMatchesTsLeDeadline", consistent, "hash", s.hash)));
        }
        rec("E7-near-deadline-inclusion", "OBSERVED", "samples", dl, "NOTE", "which block includes a tx is not controlled; this records what happened, not a guarantee");

        out.put("balanceWeiAtEnd", web3j.ethGetBalance(creds.getAddress(), DefaultBlockParameterName.LATEST).send().getBalance());
        out.put("finishedUtc", Instant.now().toString());
        flush();
        assertNotNull(lab);
    }

    void flush() throws Exception {
        Path dir = Path.of(System.getProperty("evidence.dir", "../../evidence/live"));
        Files.createDirectories(dir);
        String name = env.name() + "-chain" + chainId + "-" + Instant.now().toString().replace(":", "").replace("-", "").substring(0, 15) + ".json";
        Files.writeString(dir.resolve(name), new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(out));
        System.out.println("[LIVE] evidence written: " + name);
    }
}
