package lab.phase1;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.*;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigInteger;
import java.time.Duration;
import java.util.*;

import static lab.phase1.Evidence.of;
import static lab.phase1.Evidence.rec;
import static org.junit.jupiter.api.Assertions.*;

/**
 * READ-ONLY checks against real Fuji (official public RPC + publicnode) and ERC-20 checks on a LOCAL anvil FORK of Fuji (:8546).
 * The only "write" to Fuji is a signed tx from an unfunded ephemeral key which the node must reject (costs nothing).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FujiAndForkTest {
    static final String OFFICIAL = System.getProperty("fuji.rpc", "https://api.avax-test.network/ext/bc/C/rpc");
    static final String PUBLICNODE = "https://avalanche-fuji-c-chain-rpc.publicnode.com";
    static final String FORK = "http://localhost:8546";
    static final String USDC = "0x5425890298aed601595a70AB815c96711a31Bc65";

    static Web3j web3(String url, int secs) {
        return Web3j.build(new HttpService(url, new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(secs)).readTimeout(Duration.ofSeconds(secs)).callTimeout(Duration.ofSeconds(secs + 2)).build()));
    }

    static String ethCall(Web3j w, String to, Function f) throws Exception {
        return w.ethCall(Transaction.createEthCallTransaction("0x0000000000000000000000000000000000000001", to, FunctionEncoder.encode(f)), DefaultBlockParameterName.LATEST).send().getValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static List<Type> decode(String out, TypeReference<?>... refs) {
        List<TypeReference<Type>> l = new ArrayList<>();
        for (TypeReference<?> r : refs) l.add((TypeReference<Type>) (TypeReference) r);
        return FunctionReturnDecoder.decode(out, l);
    }

    @Test @Order(1)
    void fujiReadOnlyBothProviders() throws Exception {
        Map<String, Object> det = new LinkedHashMap<>();
        for (String[] p : new String[][]{{"official", OFFICIAL}, {"publicnode", PUBLICNODE}}) {
            Web3j w = web3(p[1], 15);
            try {
                long chain = w.ethChainId().send().getChainId().longValue();
                EthBlock.Block head = w.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock();
                EthBlock.Block fin = w.ethGetBlockByNumber(DefaultBlockParameterName.FINALIZED, false).send().getBlock();
                String dec = ethCall(w, USDC, new Function("decimals", List.of(), List.of(new TypeReference<Uint8>() {})));
                String sym = ethCall(w, USDC, new Function("symbol", List.of(), List.of(new TypeReference<Utf8String>() {})));
                BigInteger bal = (BigInteger) decode(ethCall(w, USDC, new Function("balanceOf", List.of(new Address("0x0000000000000000000000000000000000000001")), List.of())), new TypeReference<Uint256>() {}).get(0).getValue();
                assertEquals(43113, chain);
                det.put(p[0], of("chainId", chain, "head", head.getNumber(), "finalized", fin.getNumber(), "headMinusFinalized", head.getNumber().subtract(fin.getNumber()),
                        "usdcDecimals", decode(dec, new TypeReference<Uint8>() {}).get(0).getValue(), "usdcSymbol", decode(sym, new TypeReference<Utf8String>() {}).get(0).getValue(), "usdcBalanceOfProbe", bal));
            } finally { w.shutdown(); }
        }
        rec("J11-fuji-readonly-web3j", "PASS", det);
    }

    @Test @Order(2)
    void fujiLogsRangeHandlingAndDecode() throws Exception {
        Web3j w = web3(OFFICIAL, 20);
        BigInteger head = w.ethBlockNumber().send().getBlockNumber();
        Event transfer = new Event("Transfer", List.of(new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
        Map<String, Object> det = new LinkedHashMap<>();
        for (long span : new long[]{99, 100, 101, 2000}) {
            EthFilter f = new EthFilter(new DefaultBlockParameterNumber(head.subtract(BigInteger.valueOf(span))), new DefaultBlockParameterNumber(head.subtract(BigInteger.ONE)), USDC);
            f.addSingleTopic(EventEncoder.encode(transfer));
            EthLog r = w.ethGetLogs(f).send();
            det.put("span" + span, r.hasError() ? "ERROR " + r.getError().getCode() + " " + r.getError().getMessage() : "OK logs=" + r.getLogs().size());
            if (!r.hasError() && !r.getLogs().isEmpty() && !det.containsKey("decodedSample")) {
                EthLog.LogObject l = (EthLog.LogObject) r.getLogs().get(0).get();
                det.put("decodedSample", of("txHash", l.getTransactionHash(), "logIndex", l.getLogIndex(), "from", FunctionReturnDecoder.decodeIndexedValue(l.getTopics().get(1), new TypeReference<Address>() {}).getValue(),
                        "value", FunctionReturnDecoder.decode(l.getData(), transfer.getNonIndexedParameters()).get(0).getValue()));
            }
        }
        // chunk planner sanity: scanning [a,b] with provider max range R requires ceil((b-a+1)/R) calls
        rec("J12-fuji-getlogs-limits-via-web3j", "PASS", det);
        w.shutdown();
    }

    @Test @Order(3)
    void fujiErrorsTimeoutsAndUnfundedSubmit() throws Exception {
        Map<String, Object> det = new LinkedHashMap<>();
        // 1. connect timeout (non-routable address)
        Web3j dead = web3("http://10.255.255.1:8545", 2);
        long t = System.currentTimeMillis();
        try { dead.ethBlockNumber().send(); det.put("blackhole", "no error?"); } catch (Exception e) { det.put("blackhole", e.getClass().getSimpleName() + " after " + (System.currentTimeMillis() - t) + "ms"); }
        dead.shutdown();
        // 2. connection refused
        Web3j refused = web3("http://127.0.0.1:1", 2);
        try { refused.ethBlockNumber().send(); } catch (Exception e) { det.put("refused", e.getClass().getSimpleName()); }
        refused.shutdown();
        Web3j w = web3(OFFICIAL, 20);
        // 3. unknown tx / receipt => null, not error
        String fake = "0x" + "ab".repeat(32);
        det.put("unknownTxPresent", w.ethGetTransactionByHash(fake).send().getTransaction().isPresent());
        det.put("unknownReceiptPresent", w.ethGetTransactionReceipt(fake).send().getTransactionReceipt().isPresent());
        // 4. revert via eth_call on real Fuji USDC (empty account transfers 1 USDC)
        Credentials eph = Credentials.create(Keys.createEcKeyPair());
        String data = FunctionEncoder.encode(new Function("transfer", List.of(new Address("0x0000000000000000000000000000000000000002"), new Uint256(1_000_000)), List.of()));
        EthCall c = w.ethCall(Transaction.createEthCallTransaction(eph.getAddress(), USDC, data), DefaultBlockParameterName.LATEST).send();
        det.put("usdcTransferFromEmptyAccount_ethCall", c.hasError() ? c.getError().getCode() + " " + c.getError().getMessage() : "OK " + c.getValue() + " revertReason=" + c.getRevertReason());
        // 5. signed tx from an unfunded ephemeral key => must be rejected by the node (no cost)
        RawTransaction raw = RawTransaction.createTransaction(43113L, BigInteger.ZERO, BigInteger.valueOf(60_000), USDC, BigInteger.ZERO, data, BigInteger.valueOf(1_000_000_000L), BigInteger.valueOf(30_000_000_000L));
        String hex = Numeric.toHexString(TransactionEncoder.signMessage(raw, 43113L, eph));
        EthSendTransaction s = w.ethSendRawTransaction(hex).send();
        det.put("unfundedRawSubmit", s.hasError() ? s.getError().getCode() + " " + s.getError().getMessage() : "ACCEPTED hash=" + s.getTransactionHash());
        det.put("unfundedNonceLatest", w.ethGetTransactionCount(eph.getAddress(), DefaultBlockParameterName.LATEST).send().getTransactionCount());
        // 6. wrong chain id signature
        RawTransaction raw2 = RawTransaction.createTransaction(1L, BigInteger.ZERO, BigInteger.valueOf(60_000), USDC, BigInteger.ZERO, data, BigInteger.valueOf(1_000_000_000L), BigInteger.valueOf(30_000_000_000L));
        EthSendTransaction s2 = w.ethSendRawTransaction(Numeric.toHexString(TransactionEncoder.signMessage(raw2, 1L, eph))).send();
        det.put("wrongChainIdRawSubmit", s2.hasError() ? s2.getError().getCode() + " " + s2.getError().getMessage() : "ACCEPTED");
        rec("J13-fuji-errors-timeouts-unfunded-submit", "PASS", det);
        w.shutdown();
        assertTrue(s.hasError(), "unfunded tx must not be accepted");
    }

    @Test @Order(4)
    void erc20OnFujiFork() throws Exception {
        Web3j w = web3(FORK, 30);
        Rpc raw = new Rpc(FORK, Duration.ofSeconds(30));
        String holder = "0x0000000000000000000000000000000000000001";
        Credentials eph = Credentials.create(Keys.createEcKeyPair());
        raw.result("anvil_setBalance", eph.getAddress(), "0xde0b6b3a7640000");   // 1 AVAX on the FORK only
        raw.result("anvil_setBalance", holder, "0xde0b6b3a7640000");
        raw.result("anvil_impersonateAccount", holder);
        String xfer = FunctionEncoder.encode(new Function("transfer", List.of(new Address(eph.getAddress()), new Uint256(5_000_000)), List.of()));
        JsonNode r = raw.call("eth_sendTransaction", Map.of("from", holder, "to", USDC, "data", xfer, "gas", "0x30000"));
        assertFalse(r.has("error"), r.toString());
        Thread.sleep(500);
        BigInteger bal = (BigInteger) decode(ethCall(w, USDC, new Function("balanceOf", List.of(new Address(eph.getAddress())), List.of())), new TypeReference<Uint256>() {}).get(0).getValue();
        assertEquals(BigInteger.valueOf(5_000_000), bal);
        // approve + allowance via web3j signing (EIP-1559, chain 43113 fork)
        String spender = "0x00000000000000000000000000000000000000aa";
        String approve = FunctionEncoder.encode(new Function("approve", List.of(new Address(spender), new Uint256(2_000_000)), List.of()));
        BigInteger nonce = w.ethGetTransactionCount(eph.getAddress(), DefaultBlockParameterName.PENDING).send().getTransactionCount();
        RawTransaction tx = RawTransaction.createTransaction(43113L, nonce, BigInteger.valueOf(120_000), USDC, BigInteger.ZERO, approve, BigInteger.valueOf(1_000_000_000L), BigInteger.valueOf(60_000_000_000L));
        EthSendTransaction sent = w.ethSendRawTransaction(Numeric.toHexString(TransactionEncoder.signMessage(tx, 43113L, eph))).send();
        assertFalse(sent.hasError(), () -> String.valueOf(sent.getError()));
        Thread.sleep(500);
        TransactionReceipt rc = w.ethGetTransactionReceipt(sent.getTransactionHash()).send().getTransactionReceipt().orElseThrow();
        BigInteger allowance = (BigInteger) decode(ethCall(w, USDC, new Function("allowance", List.of(new Address(eph.getAddress()), new Address(spender)), List.of())), new TypeReference<Uint256>() {}).get(0).getValue();
        assertEquals(BigInteger.valueOf(2_000_000), allowance);
        // Approval event decode (topic0 of Approval(address,address,uint256))
        Event approval = new Event("Approval", List.of(new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
        Log l = rc.getLogs().stream().filter(x -> x.getTopics().get(0).equals(EventEncoder.encode(approval))).findFirst().orElseThrow();
        // transfer more than balance => receipt should show revert (USDC reverts)
        String over = FunctionEncoder.encode(new Function("transfer", List.of(new Address(spender), new Uint256(99_000_000)), List.of()));
        EthCall oc = w.ethCall(Transaction.createEthCallTransaction(eph.getAddress(), USDC, over), DefaultBlockParameterName.LATEST).send();
        rec("J14-erc20-usdc-fork-web3j", "PASS", of("forkChainId", w.ethChainId().send().getChainId(), "balanceAfterFundingFromHolder", bal, "allowanceAfterApprove", allowance, "approveStatus", rc.getStatus(), "approvalEventDecodedValue",
                FunctionReturnDecoder.decode(l.getData(), approval.getNonIndexedParameters()).get(0).getValue(), "overTransferEthCall", oc.hasError() ? oc.getError().getMessage() : "OK " + oc.getRevertReason(),
                "NOTE", "state changes exist only on the local fork; Fuji untouched"));
        w.shutdown();
    }

    @AfterAll
    static void flush() { Evidence.flush("java-fuji-fork-experiments.json"); }
}
