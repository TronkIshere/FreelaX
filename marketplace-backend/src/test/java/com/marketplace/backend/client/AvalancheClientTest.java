package com.marketplace.backend.client;

import com.marketplace.backend.configuration.AvalancheGatewayProperties;
import com.marketplace.backend.dto.response.avalanche.AvalancheResults;
import com.marketplace.backend.exception.AvalancheGatewayException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The client against a local HTTP stub that answers with the exact JSON field names the Avalanche gateway produces. */
class AvalancheClientTest {
    private HttpServer server;
    private AvalancheClient client;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> keys = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String body = "{}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            keys.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key")));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        AvalancheGatewayProperties properties = new AvalancheGatewayProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setInternalApiKey("secret-key");
        client = new AvalancheClient(new RestTemplateBuilder(), properties);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void parsesTheGatewayConfigAndSendsNoKeyOnReads() {
        body = "{\"environment\":\"LOCAL_SIMULATION\",\"isSimulated\":true,\"chainId\":31337,\"escrow\":\"0xe\",\"token\":\"0xt\",\"tokenKind\":\"MOCK\","
                + "\"contractVersion\":\"1\",\"paused\":false,\"defaultArbiter\":\"0xa\",\"backendSignersAvailable\":true,\"explorerBaseUrl\":null,\"extra\":1}";
        AvalancheResults.Config config = client.getConfig();
        assertEquals("LOCAL_SIMULATION", config.environment());
        assertTrue(config.isSimulated());
        assertEquals(31337, config.chainId());
        assertNull(config.explorerBaseUrl());
        assertEquals("GET /api/v1/avalanche/config", requests.get(0));
        assertEquals("null", keys.get(0), "reads carry no internal key");
    }

    @Test
    void postsCarryTheInternalKeyAndParseThePreparedAction() {
        body = "{\"environment\":\"LOCAL_SIMULATION\",\"isSimulated\":true,\"chainId\":31337,\"action\":\"fund\",\"escrowId\":\"0x1\","
                + "\"transactions\":[{\"purpose\":\"approve\",\"to\":\"0xt\",\"data\":\"0x1\",\"value\":\"0\"}],\"authorizationDeadline\":99,"
                + "\"typedData\":null,\"typedDataDigest\":null}";
        AvalancheResults.Prepared prepared = client.prepare("0x1", "fund", Map.of("amount", "1"));
        assertEquals(1, prepared.transactions().size());
        assertEquals(99L, prepared.authorizationDeadline());
        assertEquals("POST /api/v1/avalanche/escrows/0x1/actions/fund/prepare", requests.get(0));
        assertEquals("secret-key", keys.get(0));
    }

    @Test
    void notFoundIsEmptyForReadsAndErrorsKeepTheGatewayCode() {
        status = 404;
        body = "{\"code\":\"ESCROW_NOT_FOUND\"}";
        assertFalse(client.findEscrow("0x1").isPresent());
        assertFalse(client.findTransaction("op").isPresent());

        status = 409;
        body = "{\"timestamp\":\"2026-11-02T00:00:00Z\",\"status\":409,\"code\":\"AUTHORIZATION_UNAVAILABLE\",\"path\":\"/x\"}";
        AvalancheGatewayException conflict = assertThrows(AvalancheGatewayException.class, () -> client.prepare("0x1", "fund", Map.of()));
        assertTrue(conflict.isDefinitive());
        assertEquals(409, conflict.getHttpStatus());
        assertEquals("AUTHORIZATION_UNAVAILABLE", conflict.getCode());
    }

    @Test
    void throttlingAndServerErrorsAreNeverDefinitive() {
        for (int code : new int[]{429, 502, 503, 408, 500}) {
            status = code;
            body = "{}";
            AvalancheGatewayException e = assertThrows(AvalancheGatewayException.class, () -> client.execute("0x1", "release", Map.of("operationKey", "k")));
            assertFalse(e.isDefinitive(), "HTTP " + code);
        }
        server.stop(0);
        AvalancheGatewayException unreachable = assertThrows(AvalancheGatewayException.class, () -> client.getConfig());
        assertFalse(unreachable.isDefinitive());
        assertNull(unreachable.getHttpStatus());
    }
}
