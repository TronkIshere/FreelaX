package lab.phase1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/** Minimal direct JSON-RPC client (java.net.http) used for admin/anvil methods and for comparison with web3j. */
public final class Rpc {
    private static final ObjectMapper M = new ObjectMapper();
    private static final AtomicLong ID = new AtomicLong(1);
    private final String url;
    private final HttpClient http;
    private final Duration timeout;

    public Rpc(String url, Duration timeout) {
        this.url = url;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    /** Returns the full JSON-RPC response (result or error); throws on transport failure. */
    public JsonNode call(String method, Object... params) throws Exception {
        String body = M.writeValueAsString(java.util.Map.of("jsonrpc", "2.0", "id", ID.getAndIncrement(), "method", method, "params", Arrays.asList(params)));
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        return M.readTree(r.body());
    }

    public JsonNode result(String method, Object... params) throws Exception {
        JsonNode n = call(method, params);
        if (n.has("error")) throw new IllegalStateException(method + " -> " + n.get("error"));
        return n.get("result");
    }
}
