package com.freelax.avalanchegateway.tx;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loopback proxy in front of the local Anvil that injects the faults of the Phase 1 scenarios and of provider behaviour:
 * hold the response of eth_sendRawTransaction (the node still receives and mines it), answer receipt/by-hash lookups with null,
 * answer everything with an HTTP 429 HTML page and Retry-After, emulate a provider block-range cap on eth_getLogs, and fail
 * eth_getLogs after N requests (a crash in the middle of a scan). Counts what reaches the node.
 */
public final class FaultProxy implements AutoCloseable {

    private static final Pattern METHOD = Pattern.compile("\"method\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern RAW = Pattern.compile("\"params\"\\s*:\\s*\\[\\s*\"(0x[0-9a-fA-F]+)\"");
    private static final Pattern FROM = Pattern.compile("\"fromBlock\"\\s*:\\s*\"0x([0-9a-fA-F]+)\"");
    private static final Pattern TO = Pattern.compile("\"toBlock\"\\s*:\\s*\"0x([0-9a-fA-F]+)\"");

    private final HttpServer server;
    private final HttpClient upstream = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
    public final AtomicInteger sendCount = new AtomicInteger();
    public final AtomicInteger logRequests = new AtomicInteger();
    public final List<long[]> logRanges = new CopyOnWriteArrayList<>();
    public volatile long holdSendResponseMs;
    public volatile boolean nullLookups;
    public volatile boolean rateLimited;
    public volatile int retryAfterSeconds = 16;
    public volatile long maxLogRange;
    public volatile int failLogsAfter = -1;
    public volatile Consumer<String> onSend = raw -> { };

    public FaultProxy(String targetUrl) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Matcher method = METHOD.matcher(body);
            String name = method.find() ? method.group(1) : "";
            Matcher id = ID.matcher(body);
            String requestId = id.find() ? id.group(1) : "1";
            int status = 200;
            String contentType = "application/json";
            String response;
            if (rateLimited) {
                status = 429;
                contentType = "text/html";
                response = "<html><body>429 Too Many Requests</body></html>";
                exchange.getResponseHeaders().add("Retry-After", Integer.toString(retryAfterSeconds));
            } else if (nullLookups && (name.equals("eth_getTransactionReceipt") || name.equals("eth_getTransactionByHash"))) {
                response = "{\"jsonrpc\":\"2.0\",\"id\":" + requestId + ",\"result\":null}";
            } else if (name.equals("eth_getLogs") && logRefused(body)) {
                response = "{\"jsonrpc\":\"2.0\",\"id\":" + requestId + ",\"error\":{\"code\":-32005,\"message\":\"query exceeds max block range\"}}";
            } else if (name.equals("eth_getLogs") && failLogsAfter >= 0 && logRequests.get() > failLogsAfter) {
                status = 500;
                contentType = "text/plain";
                response = "simulated crash in the middle of a scan";
            } else {
                if (name.equals("eth_sendRawTransaction")) {
                    sendCount.incrementAndGet();
                    Matcher raw = RAW.matcher(body);
                    if (raw.find()) {
                        onSend.accept(raw.group(1));
                    }
                }
                try {
                    response = upstream.send(HttpRequest.newBuilder(URI.create(targetUrl)).timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString()).body();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    exchange.close();
                    return;
                }
                if (name.equals("eth_sendRawTransaction") && holdSendResponseMs > 0) {
                    try {
                        Thread.sleep(holdSendResponseMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException clientGone) {
                // the client timed out and closed the connection: exactly the fault being simulated
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    /** Records the requested range and reports whether the emulated provider cap refuses it. */
    private boolean logRefused(String body) {
        Matcher from = FROM.matcher(body);
        Matcher to = TO.matcher(body);
        if (!from.find() || !to.find()) {
            return false;
        }
        long start = Long.parseLong(from.group(1), 16);
        long end = Long.parseLong(to.group(1), 16);
        logRanges.add(new long[]{start, end});
        boolean refused = maxLogRange > 0 && end - start + 1 > maxLogRange;
        if (!refused) {
            logRequests.incrementAndGet();
        }
        return refused;
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void reset() {
        holdSendResponseMs = 0;
        nullLookups = false;
        rateLimited = false;
        maxLogRange = 0;
        failLogsAfter = -1;
        retryAfterSeconds = 16;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
