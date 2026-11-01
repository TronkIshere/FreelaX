package com.freelax.avalanchegateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Optional and OFF by default: the default build never depends on a public RPC.
 * Enable with AVALANCHE_TEST_FUJI=true. Sends only paced eth_chainId and web3_clientVersion (read-only) to the public Fuji RPC.
 */
@EnabledIfEnvironmentVariable(named = "AVALANCHE_TEST_FUJI", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FujiReadOnlyWebContextTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("avalanche.environment", () -> "AVALANCHE_FUJI");
        registry.add("avalanche.rpc.primary.url", () -> "https://api.avax-test.network/ext/bc/C/rpc");
        registry.add("avalanche.rpc.min-gap-ms", () -> "1500");
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void fujiIsReadThroughTheWebContext() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/avalanche/status", Map.class);
        assertEquals(200, response.getStatusCode().value(), String.valueOf(response.getBody()));
        Map<String, Object> body = response.getBody();
        assertEquals("AVALANCHE_FUJI", body.get("environment"));
        assertEquals(43113, ((Number) body.get("liveChainId")).intValue());
        assertTrue(String.valueOf(body.get("clientVersion")).startsWith("avalanchego/"), String.valueOf(body.get("clientVersion")));
    }
}
