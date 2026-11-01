package com.freelax.avalanchegateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real embedded web server on a random port, real Spring-managed web3j client, real HTTP JSON-RPC to a loopback stub.
 * web3j deserialises the responses with Jackson 3 while Spring MVC serialises the status with Jackson 2: the exact
 * combination that failed with NoClassDefFoundError in Phase 1 when the jackson-annotations override was missing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebContextStubTest {

    private static final StubRpcServer STUB = startStub();

    private static StubRpcServer startStub() {
        try {
            return StubRpcServer.start(31337, "anvil/v1.8.7");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("avalanche.environment", () -> "LOCAL_SIMULATION");
        registry.add("avalanche.rpc.primary.url", STUB::url);
        registry.add("avalanche.rpc.min-gap-ms", () -> "0");
    }

    @AfterAll
    static void stop() {
        STUB.close();
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void statusEndpointMakesLiveWeb3jCallsThroughTheWebContext() {
        int before = STUB.requestCount();
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/avalanche/status", Map.class);

        assertEquals(200, response.getStatusCode().value(), String.valueOf(response.getBody()));
        Map<String, Object> body = response.getBody();
        assertEquals("LOCAL_SIMULATION", body.get("environment"));
        assertEquals(true, body.get("isSimulated"));
        assertEquals(31337, ((Number) body.get("validatedChainId")).intValue());
        assertEquals(31337, ((Number) body.get("liveChainId")).intValue());
        assertEquals("anvil/v1.8.7", body.get("clientVersion"));
        assertEquals("127.0.0.1", body.get("endpointHost"));
        assertEquals(Runtime.version().toString(), body.get("javaVersion"));
        assertEquals(2, STUB.requestCount() - before, "the request itself performed eth_chainId and web3_clientVersion");
        assertTrue(STUB.methods().stream().allMatch(m -> m.equals("eth_chainId") || m.equals("web3_clientVersion")),
                "only read-only identity calls are sent: " + STUB.methods());
    }
}
