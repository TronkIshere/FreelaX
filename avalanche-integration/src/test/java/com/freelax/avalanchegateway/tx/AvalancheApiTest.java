package com.freelax.avalanchegateway.tx;

import com.freelax.avalanchegateway.AvalancheGatewayApplication;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.signer.SignerRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The gateway API over HTTP against the real contract, a real Anvil and MySQL. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AvalancheGatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AvalancheApiTest {

    private static final String KEY = "test-internal-key";
    private static final ContractFixtures.Stack STACK;
    private static final Path MANIFEST;

    static {
        LocalStack.start();
        STACK = ContractFixtures.deployStack(LocalStack.anvilUrl(), ContractFixtures.ANVIL_ACCOUNT_0);
        try {
            MANIFEST = ContractFixtures.writeManifest(Files.createTempFile("avalanche-manifest", ".json"), "LOCAL_SIMULATION", 31337, STACK, 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("avalanche.environment", () -> "LOCAL_SIMULATION");
        registry.add("avalanche.rpc.primary.url", LocalStack::anvilUrl);
        registry.add("avalanche.rpc.min-gap-ms", () -> "0");
        registry.add("avalanche.signers.enabled", () -> "true");
        registry.add("avalanche.tx.enabled", () -> "true");
        registry.add("avalanche.tx.reconcile-interval-ms", () -> "200");
        registry.add("avalanche.db.url", LocalStack::jdbcUrl);
        registry.add("avalanche.db.username", LocalStack::dbUser);
        registry.add("avalanche.db.password", LocalStack::dbPassword);
        registry.add("avalanche.deployment-manifest", () -> MANIFEST.toString());
        registry.add("avalanche.internal-api-key", () -> KEY);
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    SignerRegistry registry;
    private static boolean prepared;

    @BeforeAll
    static void clean() {
        JdbcTemplate jdbc = new JdbcTemplate(LocalStack.dataSource());
        jdbc.update("DELETE FROM avalanche_tx");
        jdbc.update("DELETE FROM avalanche_nonce");
    }

    private synchronized void prepareChain() {
        if (prepared) {
            return;
        }
        String url = LocalStack.anvilUrl();
        for (SignerRole role : SignerRole.values()) {
            AnvilRpc.fund(url, registry.get(role).address());
        }
        ContractFixtures.sendFromAdmin(url, STACK.escrow(), FunctionEncoder.encode(new Function("grantRole", List.of(
                new Bytes32(Numeric.hexStringToByteArray(Hash.sha3String("AUTH_SIGNER_ROLE"))),
                new Address(registry.get(SignerRole.AUTHORIZATION).address())), List.of())));
        ContractFixtures.sendFromAdmin(url, STACK.escrow(), FunctionEncoder.encode(new Function("setDefaultArbiter",
                List.of(new Address(registry.get(SignerRole.ARBITER).address())), List.of())));
        prepared = true;
    }

    // ---- HTTP helpers ----------------------------------------------------------------------------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Object> exchange(TestRestTemplate template, HttpMethod method, String path, String key, String body, int expectedStatus) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            headers.set("X-Internal-Api-Key", key);
        }
        ResponseEntity<Map> response = template.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
        assertEquals(expectedStatus, response.getStatusCode().value(), path + " -> " + response.getBody());
        return response.getBody();
    }

    private Map<String, Object> get(String path) {
        return exchange(rest, HttpMethod.GET, path, null, null, 200);
    }

    private Map<String, Object> post(String path, String body) {
        return exchange(rest, HttpMethod.POST, path, KEY, body, 200);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> awaitState(String operationKey, String... accepted) {
        Map<String, Object> last = null;
        for (int i = 0; i < 150; i++) {
            last = get("/api/v1/avalanche/transactions/" + operationKey);
            if (List.of(accepted).contains(last.get("state"))) {
                return last;
            }
            sleep(100);
        }
        throw new AssertionError("operation " + operationKey + " stayed " + last);
    }

    private String buyer() {
        return registry.get(SignerRole.TEST_BUYER).address().toLowerCase(Locale.ROOT);
    }

    private String freelancer() {
        return registry.get(SignerRole.TEST_FREELANCER).address().toLowerCase(Locale.ROOT);
    }

    private String newEscrowId() {
        return "0x" + UUID.randomUUID().toString().replace("-", "") + "0".repeat(32);
    }

    private void execute(String escrowId, String action, String bodyFields) {
        String key = action + "-" + UUID.randomUUID();
        post("/api/v1/avalanche/escrows/" + escrowId + "/actions/" + action + "/execute", "{\"operationKey\":\"" + key + "\"" + bodyFields + "}");
        assertEquals("CONFIRMED", awaitState(key, "CONFIRMED", "FINALIZED", "REVERTED").get("state").equals("REVERTED") ? "REVERTED" : "CONFIRMED", action + " was mined successfully");
    }

    private void mintTo(String address, long amount) {
        Map<String, Object> faucet = post("/api/v1/avalanche/assets/faucet", "{\"address\":\"" + address + "\",\"amount\":\"" + amount + "\"}");
        @SuppressWarnings("unchecked") Map<String, Object> tx = (Map<String, Object>) faucet.get("transaction");
        awaitState((String) tx.get("operationKey"), "CONFIRMED", "FINALIZED");
    }

    private String fundFields(long amount) {
        long now = AnvilRpc.blockTimestamp(LocalStack.anvilUrl());
        return ",\"client\":\"" + buyer() + "\",\"freelancer\":\"" + freelancer() + "\",\"amount\":\"" + amount + "\",\"fundingExpiresAt\":" + (now + 3600)
                + ",\"deliveryDueAt\":" + (now + 86400 * 7) + ",\"reviewWindowHours\":72,\"maxRevisions\":2,\"highValueGrace\":false";
    }

    private String status(String escrowId) {
        return (String) get("/api/v1/avalanche/escrows/" + escrowId).get("status");
    }

    // ---- tests -------------------------------------------------------------------------------------------------------

    @Test
    void configNamesTheEnvironmentAndSaysTheMoneyIsSimulated() {
        prepareChain();
        Map<String, Object> config = get("/api/v1/avalanche/config");
        assertEquals("LOCAL_SIMULATION", config.get("environment"));
        assertEquals(true, config.get("isSimulated"));
        assertEquals(31337, ((Number) config.get("chainId")).intValue());
        assertEquals(STACK.escrow(), config.get("escrow"));
        assertEquals(STACK.usdc(), config.get("token"));
        assertEquals("MOCK", config.get("tokenKind"));
        assertEquals(true, config.get("backendSignersAvailable"));
        assertEquals(false, config.get("paused"));
        assertNull(config.get("explorerBaseUrl"), "a local chain has no explorer");
        assertEquals(registry.get(SignerRole.ARBITER).address().toLowerCase(Locale.ROOT), config.get("defaultArbiter"));
        assertTrue(config.keySet().containsAll(List.of("environment", "isSimulated", "chainId", "escrow", "token", "tokenKind", "contractVersion",
                "paused", "defaultArbiter", "backendSignersAvailable", "explorerBaseUrl")), "the marketplace client reads these fields: " + config.keySet());
    }

    @Test
    void everyMutatingCallNeedsTheInternalKeyAndNoRequestCanCarryAKeyOrAChain() {
        prepareChain();
        String body = "{\"address\":\"" + buyer() + "\",\"amount\":\"1000000\"}";
        exchange(rest, HttpMethod.POST, "/api/v1/avalanche/assets/faucet", null, body, 401);
        exchange(rest, HttpMethod.POST, "/api/v1/avalanche/assets/faucet", "wrong-key", body, 401);
        for (String smuggled : new String[]{"\"privateKey\":\"0x01\"", "\"rpcUrl\":\"http://example\"", "\"chainId\":43114"}) {
            exchange(rest, HttpMethod.POST, "/api/v1/avalanche/assets/faucet", KEY,
                    "{\"address\":\"" + buyer() + "\",\"amount\":\"1\"," + smuggled + "}", 400);
        }
        exchange(rest, HttpMethod.POST, "/api/v1/avalanche/escrows/" + newEscrowId() + "/actions/release/prepare", KEY,
                "{\"chainId\":43114}", 400);
        get("/api/v1/avalanche/config");
    }

    @Test
    void inputsAreValidated() {
        prepareChain();
        exchange(rest, HttpMethod.GET, "/api/v1/avalanche/escrows/0x123", null, null, 400);
        exchange(rest, HttpMethod.GET, "/api/v1/avalanche/escrows/" + newEscrowId(), null, null, 404);
        exchange(rest, HttpMethod.POST, "/api/v1/avalanche/escrows/" + newEscrowId() + "/actions/transfer-everything/prepare", KEY, "{}", 400);
        exchange(rest, HttpMethod.GET, "/api/v1/avalanche/assets/balance?address=nope", null, null, 400);
        exchange(rest, HttpMethod.GET, "/api/v1/avalanche/transactions/by-hash/0x12", null, null, 400);
        exchange(rest, HttpMethod.GET, "/api/v1/avalanche/transactions/never-submitted", null, null, 404);
        assertEquals(true, get("/api/v1/avalanche/addresses/validate?address=" + buyer()).get("valid"));
        assertEquals(false, get("/api/v1/avalanche/addresses/validate?address=0x12").get("valid"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theFullEscrowLifecycleThroughTheApiOnly() throws Exception {
        prepareChain();
        mintTo(buyer(), 300_000_000L);
        Map<String, Object> balance = get("/api/v1/avalanche/assets/balance?address=" + buyer());
        assertEquals("300000000", balance.get("balanceBaseUnits"));
        assertEquals(6, ((Number) balance.get("decimals")).intValue());

        // 1. fund (prepare shows what a user's own wallet would sign; execute uses the LOCAL development wallet)
        String one = newEscrowId();
        Map<String, Object> prepared = post("/api/v1/avalanche/escrows/" + one + "/actions/fund/prepare", "{" + fundFields(100_000_000L).substring(1) + "}");
        List<Map<String, Object>> txs = (List<Map<String, Object>>) prepared.get("transactions");
        assertEquals(List.of("approve", "fund"), txs.stream().map(t -> t.get("purpose")).toList());
        assertEquals(STACK.usdc(), txs.get(0).get("to"));
        assertEquals(STACK.escrow(), txs.get(1).get("to"));
        assertNotNull(prepared.get("authorizationDeadline"));
        execute(one, "fund", fundFields(100_000_000L));
        assertEquals("FUNDED", status(one));
        Map<String, Object> funded = get("/api/v1/avalanche/escrows/" + one);
        assertTrue(funded.keySet().containsAll(List.of("environment", "isSimulated", "chainId", "escrowId", "status", "client", "freelancer", "arbiter",
                "amountBaseUnits", "maxRevisions", "revisionsUsed", "submissionCount", "deliveryDueAt", "reviewDueAt", "submissionHash", "resolutionHash")),
                "the marketplace client reads these fields: " + funded.keySet());
        assertEquals("100000000", funded.get("amountBaseUnits"));
        assertEquals(registry.get(SignerRole.ARBITER).address().toLowerCase(Locale.ROOT), funded.get("arbiter"));

        // 2. submit, revise, resubmit, release by the client
        execute(one, "submit", ",\"evidenceHash\":\"" + Hash.sha3String("v1") + "\"");
        assertEquals("SUBMITTED", status(one));
        execute(one, "revise", ",\"feedbackHash\":\"" + Hash.sha3String("fb") + "\"");
        assertEquals("REVISION", status(one));
        execute(one, "submit", ",\"evidenceHash\":\"" + Hash.sha3String("v2") + "\"");
        execute(one, "release", ",\"by\":\"client\"");
        assertEquals("RELEASED", status(one));
        assertEquals("100000000", get("/api/v1/avalanche/assets/balance?address=" + freelancer()).get("balanceBaseUnits"));

        // 3. dispute by the freelancer and an arbiter refund
        String two = newEscrowId();
        execute(two, "fund", fundFields(50_000_000L));
        execute(two, "submit", ",\"evidenceHash\":\"" + Hash.sha3String("w") + "\"");
        execute(two, "dispute", ",\"by\":\"freelancer\",\"reasonHash\":\"" + Hash.sha3String("why") + "\"");
        assertEquals("DISPUTED", status(two));
        execute(two, "resolve", ",\"releaseToFreelancer\":false,\"resolutionHash\":\"" + Hash.sha3String("d") + "\"");
        assertEquals("REFUNDED", status(two));

        // 4. mutual refund: prepare returns the typed data for the wallets; execute signs with the two development wallets
        String three = newEscrowId();
        execute(three, "fund", fundFields(20_000_000L));
        Map<String, Object> mutual = post("/api/v1/avalanche/escrows/" + three + "/actions/mutual-refund/prepare", "{}");
        assertTrue(((String) mutual.get("typedData")).contains("\"primaryType\":\"MutualRefund\""));
        assertTrue(((String) mutual.get("typedDataDigest")).startsWith("0x"));
        assertTrue(((List<?>) mutual.get("transactions")).isEmpty(), "no calldata until both signatures exist");
        execute(three, "mutual-refund", "");
        assertEquals("REFUNDED", status(three));

        // 4b. F13 regression: each party signs ONE typed data with its own wallet, blocks are mined between the two
        //     signatures, and the call carrying both signatures reuses the first deadline (before the fix it chose a new one)
        String four = newEscrowId();
        execute(four, "fund", fundFields(10_000_000L));
        String prepareFour = "/api/v1/avalanche/escrows/" + four + "/actions/mutual-refund/prepare";
        Map<String, Object> first = post(prepareFour, "{}");
        long deadline = ((Number) first.get("authorizationDeadline")).longValue();
        String typed = (String) first.get("typedData");
        String clientSig = com.freelax.avalanchegateway.contract.EscrowAuthorizations.sign(registry.get(SignerRole.TEST_BUYER), typed).toHex();
        AnvilRpc.mine(LocalStack.anvilUrl());
        AnvilRpc.mine(LocalStack.anvilUrl());
        Map<String, Object> second = post(prepareFour, "{\"authorizationDeadline\":" + deadline + "}");
        assertEquals(typed, second.get("typedData"), "the second signer signs the same typed data");
        String freelancerSig = com.freelax.avalanchegateway.contract.EscrowAuthorizations.sign(registry.get(SignerRole.TEST_FREELANCER),
                (String) second.get("typedData")).toHex();
        String both = ",\"clientSignature\":\"" + clientSig + "\",\"freelancerSignature\":\"" + freelancerSig + "\"";
        assertEquals("DEADLINE_REQUIRED", exchange(rest, HttpMethod.POST, prepareFour, KEY, "{" + both.substring(1) + "}", 400).get("code"));
        assertEquals("DEADLINE_OUT_OF_RANGE", exchange(rest, HttpMethod.POST, prepareFour, KEY,
                "{" + both.substring(1) + ",\"authorizationDeadline\":" + (deadline + 100_000) + "}", 400).get("code"));
        Map<String, Object> calldata = post(prepareFour, "{" + both.substring(1) + ",\"authorizationDeadline\":" + deadline + "}");
        assertEquals(1, ((List<?>) calldata.get("transactions")).size(), "both signatures over the same deadline give calldata");
        execute(four, "mutual-refund", both + ",\"authorizationDeadline\":" + deadline);
        assertEquals("REFUNDED", status(four));

        // 4b-2. R12 mutual settlement of a Disputed escrow: refused before the dispute, then both development wallets sign
        String settled = newEscrowId();
        execute(settled, "fund", fundFields(15_000_000L));
        String settleFields = "\"releaseToFreelancer\":true,\"resolutionHash\":\"" + Hash.sha3String("settled") + "\"";
        String prepareSettled = "/api/v1/avalanche/escrows/" + settled + "/actions/mutual-settle/prepare";
        assertEquals("ESCROW_NOT_DISPUTED", exchange(rest, HttpMethod.POST, prepareSettled, KEY, "{" + settleFields + "}", 409).get("code"));
        execute(settled, "submit", ",\"evidenceHash\":\"" + Hash.sha3String("w-settle") + "\"");
        execute(settled, "dispute", ",\"by\":\"client\",\"reasonHash\":\"" + Hash.sha3String("late") + "\"");
        Map<String, Object> settle = post(prepareSettled, "{" + settleFields + "}");
        assertTrue(((String) settle.get("typedData")).contains("\"primaryType\":\"MutualSettlement\""));
        assertTrue(((List<?>) settle.get("transactions")).isEmpty(), "no calldata until both signatures exist");
        execute(settled, "mutual-settle", "," + settleFields);
        assertEquals("RELEASED", status(settled));

        // 4c. wallet linking (A8): EIP-191 personal_sign verification endpoint
        org.web3j.crypto.ECKeyPair linkKey = org.web3j.crypto.Keys.createEcKeyPair();
        String linkAddress = "0x" + org.web3j.crypto.Keys.getAddress(linkKey);
        String challenge = "FreelaX EVM wallet binding\nNonce: " + UUID.randomUUID();
        org.web3j.crypto.Sign.SignatureData sd = org.web3j.crypto.Sign.signPrefixedMessage(challenge.getBytes(java.nio.charset.StandardCharsets.UTF_8), linkKey);
        String linkSig = org.web3j.utils.Numeric.toHexString(sd.getR()) + org.web3j.utils.Numeric.toHexStringNoPrefix(sd.getS())
                + org.web3j.utils.Numeric.toHexStringNoPrefix(sd.getV());
        String verifyBody = "{\"address\":\"" + linkAddress + "\",\"message\":\"" + challenge.replace("\n", "\\n") +"\",\"signature\":\"" + linkSig + "\"}";
        assertEquals(true, post("/api/v1/avalanche/wallet-signatures/verify", verifyBody).get("valid"));
        assertEquals(false, post("/api/v1/avalanche/wallet-signatures/verify", verifyBody.replace("Nonce", "nonce")).get("valid"));
        exchange(rest, HttpMethod.POST, "/api/v1/avalanche/wallet-signatures/verify", KEY,
                verifyBody.replace("}", ",\"privateKey\":\"x\"}"), 400);

        // 4d. P3-C4 stuck-and-reported (O-6): issuer controls on MockUSDC are reported, read-only; lifting them restores the
        //     normal path; nothing is withdrawn, bypassed or rerouted
        String five = newEscrowId();
        execute(five, "fund", fundFields(7_000_000L));
        String alertsPath = "/api/v1/avalanche/escrows/" + five + "/alerts";
        assertTrue(((List<?>) get(alertsPath).get("alerts")).isEmpty(), "an unblocked escrow has no alerts");
        String token = STACK.usdc();
        ContractFixtures.sendFromAdmin(LocalStack.anvilUrl(), token, FunctionEncoder.encode(new Function("setBlacklisted",
                List.of(new Address(freelancer()), new org.web3j.abi.datatypes.Bool(true)), List.of())));
        ContractFixtures.sendFromAdmin(LocalStack.anvilUrl(), token, FunctionEncoder.encode(new Function("setPaused",
                List.of(new org.web3j.abi.datatypes.Bool(true)), List.of())));
        AnvilRpc.mine(LocalStack.anvilUrl());
        Map<String, Object> stuck = get(alertsPath);
        List<?> codes = ((List<?>) stuck.get("alerts")).stream().map(x -> ((Map<?, ?>) x).get("code")).toList();
        assertEquals(List.of("TOKEN_PAUSED", "FREELANCER_BLACKLISTED"), codes);
        assertEquals("FUNDED", stuck.get("escrowStatus"));
        assertTrue(((String) stuck.get("note")).contains("no privileged withdrawal, bypass or rerouting"));
        ContractFixtures.sendFromAdmin(LocalStack.anvilUrl(), token, FunctionEncoder.encode(new Function("setPaused",
                List.of(new org.web3j.abi.datatypes.Bool(false)), List.of())));
        ContractFixtures.sendFromAdmin(LocalStack.anvilUrl(), token, FunctionEncoder.encode(new Function("setBlacklisted",
                List.of(new Address(freelancer()), new org.web3j.abi.datatypes.Bool(false)), List.of())));
        AnvilRpc.mine(LocalStack.anvilUrl());
        assertTrue(((List<?>) get(alertsPath).get("alerts")).isEmpty(), "lifting the controls clears the report");
        execute(five, "submit", ",\"evidenceHash\":\"" + Hash.sha3String("w5") + "\"");
        execute(five, "release", ",\"by\":\"client\"");
        assertEquals("RELEASED", status(five), "the normal release succeeds once the issuer lifts the controls");
        assertEquals(404, exchange(rest, HttpMethod.GET, "/api/v1/avalanche/escrows/" + newEscrowId() + "/alerts", null, null, 404).get("status"));

        // 5. status of a transaction broadcast elsewhere is derived live from the chain
        Map<String, Object> firstTx = get("/api/v1/avalanche/transactions/" + awaitFirstKey());
        assertTrue(firstTx.keySet().containsAll(List.of("environment", "isSimulated", "chainId", "operationKey", "state", "txHash", "nonce",
                "blockNumber", "errorClass", "signerRole")), "the marketplace client reads these fields: " + firstTx.keySet());
        String hash = (String) firstTx.get("txHash");
        Map<String, Object> byHash = get("/api/v1/avalanche/transactions/by-hash/" + hash);
        assertTrue(List.of("CONFIRMED", "FINALIZED").contains(byHash.get("state")), String.valueOf(byHash));
        assertEquals("UNKNOWN", get("/api/v1/avalanche/transactions/by-hash/0x" + "ab".repeat(32)).get("state"), "an unknown hash is UNKNOWN, never failed");

        // 6. a successful receipt for a contract outside the deployment manifest is not a transaction of this deployment
        String foreign = ContractFixtures.sendFromAdmin(LocalStack.anvilUrl(), "0x" + "5a".repeat(20), "0x");
        AnvilRpc.mine(LocalStack.anvilUrl());
        AnvilRpc.mine(LocalStack.anvilUrl());
        Map<String, Object> foreignStatus = get("/api/v1/avalanche/transactions/by-hash/" + foreign);
        assertEquals("UNKNOWN", foreignStatus.get("state"), "a receipt for a non-manifest target never reads as CONFIRMED or FINALIZED");
        assertEquals(null, foreignStatus.get("blockNumber"));
    }

    /** Payout rail: a user's own token transfer, verified read-only from the receipt logs; never a fiat payout confirmation. */
    @Test
    void aUsersOwnTokenTransferIsVerifiedFromTheReceiptLogs() {
        String url = LocalStack.anvilUrl();
        String sender = ContractFixtures.ANVIL_ACCOUNT_0.toLowerCase(Locale.ROOT);
        String partner = "0x" + "7e".repeat(20);
        ContractFixtures.sendFromAdmin(url, STACK.usdc(), FunctionEncoder.encode(new Function("mint",
                List.of(new Address(sender), new org.web3j.abi.datatypes.generated.Uint256(5_000_000)), List.of())));
        String tx = ContractFixtures.sendFromAdmin(url, STACK.usdc(), FunctionEncoder.encode(new Function("transfer",
                List.of(new Address(partner), new org.web3j.abi.datatypes.generated.Uint256(2_500_000)), List.of())));
        AnvilRpc.mine(url);
        AnvilRpc.mine(url);
        String base = "/api/v1/avalanche/token-transfers/";

        Map<String, Object> ok = get(base + tx + "?from=" + sender + "&to=" + partner + "&amount=2500000");
        assertEquals("MATCHED", ok.get("match"), String.valueOf(ok));
        assertEquals("FINALIZED", ok.get("state"));
        assertEquals(true, ok.get("isSimulated"));
        assertEquals(STACK.usdc().toLowerCase(Locale.ROOT), ok.get("token"));
        assertNotNull(ok.get("blockNumber"));

        assertEquals("AMOUNT_MISMATCH", get(base + tx + "?from=" + sender + "&to=" + partner + "&amount=2500001").get("match"));
        assertEquals("TRANSFER_NOT_FOUND", get(base + tx + "?from=" + sender + "&to=0x" + "7f".repeat(20) + "&amount=2500000").get("match"),
                "another recipient is not proven");
        assertEquals("TRANSFER_NOT_FOUND", get(base + tx + "?from=0x" + "11".repeat(20) + "&to=" + partner + "&amount=2500000").get("match"),
                "another sender is not proven");

        // a transfer larger than the balance is mined and reverts: never a match
        String reverted = ContractFixtures.sendFromAdmin(url, STACK.usdc(), FunctionEncoder.encode(new Function("transfer",
                List.of(new Address(partner), new org.web3j.abi.datatypes.generated.Uint256(new java.math.BigInteger("1000000000000000"))), List.of())));
        AnvilRpc.mine(url);
        Map<String, Object> rev = get(base + reverted + "?from=" + sender + "&to=" + partner + "&amount=1000000000000000");
        assertEquals("REVERTED", rev.get("state"));
        assertEquals("TRANSFER_NOT_FOUND", rev.get("match"));

        // a receipt of a non-token target, and an unknown hash, prove nothing
        String foreign = ContractFixtures.sendFromAdmin(url, "0x" + "5b".repeat(20), "0x");
        AnvilRpc.mine(url);
        Map<String, Object> other = get(base + foreign + "?from=" + sender + "&to=" + partner + "&amount=1");
        assertEquals("UNKNOWN", other.get("state"));
        assertEquals("TRANSFER_NOT_FOUND", other.get("match"));
        Map<String, Object> unknown = get(base + "0x" + "cd".repeat(32) + "?from=" + sender + "&to=" + partner + "&amount=1");
        assertEquals("UNKNOWN", unknown.get("state"));
        assertEquals("NO_RECEIPT", unknown.get("match"));

        // inputs
        exchange(rest, HttpMethod.GET, base + "0x12?from=" + sender + "&to=" + partner + "&amount=1", null, null, 400);
        exchange(rest, HttpMethod.GET, base + tx + "?from=" + sender + "&to=" + partner + "&amount=0", null, null, 400);
        exchange(rest, HttpMethod.GET, base + tx + "?from=" + sender + "&to=" + partner + "&amount=abc", null, null, 400);
        exchange(rest, HttpMethod.GET, base + tx + "?from=" + sender + "&to=" + sender + "&amount=1", null, null, 400);
        exchange(rest, HttpMethod.GET, base + tx + "?from=nope&to=" + partner + "&amount=1", null, null, 400);
    }

    private String awaitFirstKey() {
        return new JdbcTemplate(LocalStack.dataSource()).queryForObject("SELECT operation_key FROM avalanche_tx ORDER BY id LIMIT 1", String.class);
    }

    @Test
    void anOperationKeyIsIdempotentAndAConflictingReuseIsRefused() {
        prepareChain();
        String address = buyer();
        String key = "idem-" + UUID.randomUUID();
        // the faucet generates its own key, so use the action endpoint for idempotency
        String escrowId = newEscrowId();
        mintTo(address, 40_000_000L);
        String fields = fundFields(40_000_000L);
        Map<String, Object> first = post("/api/v1/avalanche/escrows/" + escrowId + "/actions/fund/execute", "{\"operationKey\":\"" + key + "\"" + fields + "}");
        awaitState(key, "CONFIRMED", "FINALIZED");
        // a new block with a later timestamp makes a regenerated authorization differ from the first one
        sleep(1200);
        AnvilRpc.mine(LocalStack.anvilUrl());
        Map<String, Object> again = post("/api/v1/avalanche/escrows/" + escrowId + "/actions/fund/execute", "{\"operationKey\":\"" + key + "\"" + fields + "}");
        assertEquals(first.get("txHash"), again.get("txHash"));
        assertEquals(first.get("nonce"), again.get("nonce"));
        assertFalse(first.get("operationKey").toString().isEmpty());
    }

    @Test
    void aProcessWithoutBackendSignersCannotAuthorizeOrExecuteAndSaysSo() {
        prepareChain();
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(AvalancheGatewayApplication.class).web(WebApplicationType.SERVLET)
                .run("--server.port=0", "--avalanche.rpc.min-gap-ms=0", "--avalanche.environment=LOCAL_SIMULATION",
                        "--avalanche.rpc.primary.url=" + LocalStack.anvilUrl(), "--avalanche.tx.enabled=true",
                        "--avalanche.db.url=" + LocalStack.jdbcUrl(), "--avalanche.db.username=" + LocalStack.dbUser(),
                        "--avalanche.db.password=" + LocalStack.dbPassword(), "--avalanche.deployment-manifest=" + MANIFEST,
                        "--avalanche.internal-api-key=" + KEY)) {
            String base = "http://localhost:" + ctx.getEnvironment().getProperty("local.server.port");
            TestRestTemplate bare = new TestRestTemplate();
            Map<String, Object> config = exchange(bare, HttpMethod.GET, base + "/api/v1/avalanche/config", null, null, 200);
            assertEquals(false, config.get("backendSignersAvailable"));
            Map<String, Object> prepare = exchange(bare, HttpMethod.POST, base + "/api/v1/avalanche/escrows/" + newEscrowId() + "/actions/fund/prepare",
                    KEY, "{" + fundFields(1_000_000L).substring(1) + "}", 409);
            assertEquals("AUTHORIZATION_UNAVAILABLE", prepare.get("code"));
            Map<String, Object> execute = exchange(bare, HttpMethod.POST, base + "/api/v1/avalanche/escrows/" + newEscrowId() + "/actions/release/execute",
                    KEY, "{\"operationKey\":\"x-1\"}", 409);
            assertEquals("SIGNER_UNAVAILABLE", execute.get("code"));
            Map<String, Object> faucet = exchange(bare, HttpMethod.POST, base + "/api/v1/avalanche/assets/faucet", KEY,
                    "{\"address\":\"" + buyer() + "\",\"amount\":\"1\"}", 409);
            assertEquals("SIGNER_UNAVAILABLE", faucet.get("code"));
            // read-only calls and actions that need no authorization still work: a wallet can sign the prepared release itself
            Map<String, Object> release = exchange(bare, HttpMethod.POST, base + "/api/v1/avalanche/escrows/" + newEscrowId() + "/actions/release/prepare",
                    KEY, "{}", 200);
            assertEquals(1, ((List<?>) release.get("transactions")).size());
        }
    }
}
