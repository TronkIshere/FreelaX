package lab.phase1web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.web3j.protocol.Web3j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Real embedded web server (random port), real Spring-managed web3j bean, real JSON-RPC call to Fuji (read-only), JSON served via Jackson 2. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "phase1.rpc-url=https://api.avax-test.network/ext/bc/C/rpc")
class WebContextTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired Web3j web3j;
    @Autowired Environment env;

    static String jar(String cls) {
        try { String u = Class.forName(cls).getProtectionDomain().getCodeSource().getLocation().toString(); return u; }
        catch (ClassNotFoundException e) { return "/(absent)"; }
    }

    @Test
    @SuppressWarnings("unchecked")
    void webContextStartsAndSpringManagedWeb3jCallsFuji() throws Exception {
        // Dependency facts at runtime (which Jackson jars are actually on the classpath)
        String annotationsJar = com.fasterxml.jackson.annotation.JsonProperty.class.getProtectionDomain().getCodeSource().getLocation().toString();
        String databind2 = com.fasterxml.jackson.databind.ObjectMapper.class.getProtectionDomain().getCodeSource().getLocation().toString();
        String databind3 = jar("tools.jackson.databind.ObjectMapper");
        System.out.println("[CLASSPATH] annotations=" + annotationsJar.substring(annotationsJar.lastIndexOf('/') + 1)
                + " databind2=" + databind2.substring(databind2.lastIndexOf('/') + 1) + " databind3=" + databind3.substring(databind3.lastIndexOf('/') + 1));

        Map<String, Object> body = rest.getForObject("http://localhost:" + port + "/chain", Map.class);
        System.out.println("[RESULT] " + body);
        assertNotNull(body, "controller returned null (RPC or serialization failure, see log)");
        assertEquals(43113, ((Number) body.get("chainId")).intValue(), "environment guard: only Fuji (43113) accepted in this lab");
        assertTrue(((Number) body.get("head")).longValue() > 0);
        assertEquals(System.getProperty("java.version"), body.get("java"));
        Files.writeString(Path.of(System.getProperty("evidence.file", "target/web-result.json")), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                Map.of("result", body, "annotationsJar", annotationsJar.substring(annotationsJar.lastIndexOf('/') + 1), "databind2", databind2.substring(databind2.lastIndexOf('/') + 1),
                        "databind3", databind3.substring(databind3.lastIndexOf('/') + 1))));
    }
}
