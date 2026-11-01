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

/** Optional: real Anvil. Enable with AVALANCHE_TEST_ANVIL_URL=http://127.0.0.1:8545 (a plain local Anvil). */
@EnabledIfEnvironmentVariable(named = "AVALANCHE_TEST_ANVIL_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnvilWebContextTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("avalanche.environment", () -> "LOCAL_SIMULATION");
        registry.add("avalanche.rpc.primary.url", () -> System.getenv("AVALANCHE_TEST_ANVIL_URL"));
        registry.add("avalanche.rpc.min-gap-ms", () -> "0");
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void realAnvilIsReadThroughTheWebContext() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/avalanche/status", Map.class);
        assertEquals(200, response.getStatusCode().value(), String.valueOf(response.getBody()));
        Map<String, Object> body = response.getBody();
        assertEquals("LOCAL_SIMULATION", body.get("environment"));
        assertEquals(31337, ((Number) body.get("liveChainId")).intValue());
        assertTrue(String.valueOf(body.get("clientVersion")).startsWith("anvil/"), String.valueOf(body.get("clientVersion")));
    }
}
