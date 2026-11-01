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
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loopback proxy in front of the local Anvil that injects the faults of the Phase 1 scenarios: hold the response of
 * eth_sendRawTransaction (the node still receives and mines it), answer receipt/by-hash lookups with null, answer
 * everything with an HTTP 429 HTML page and Retry-After. Counts the raw transactions that reach the node.
 */
final class FaultProxy implements AutoCloseable {

    private static final Pattern METHOD = Pattern.compile("\"method\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern RAW = Pattern.compile("\"params\"\\s*:\\s*\\[\\s*\"(0x[0-9a-fA-F]+)\"");

    private final HttpServer server;
    private final HttpClient upstream = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
    final AtomicInteger sendCount = new AtomicInteger();
    volatile long holdSendResponseMs;
    volatile boolean nullLookups;
    volatile boolean rateLimited;
    volatile Consumer<String> onSend = raw -> { };

    FaultProxy(String targetUrl) throws IOException {
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
                exchange.getResponseHeaders().add("Retry-After", "16");
            } else if (nullLookups && (name.equals("eth_getTransactionReceipt") || name.equals("eth_getTransactionByHash"))) {
                response = "{\"jsonrpc\":\"2.0\",\"id\":" + requestId + ",\"result\":null}";
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

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void reset() {
        holdSendResponseMs = 0;
        nullLookups = false;
        rateLimited = false;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
