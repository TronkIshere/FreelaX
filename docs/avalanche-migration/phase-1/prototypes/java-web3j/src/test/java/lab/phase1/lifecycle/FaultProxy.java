package lab.phase1.lifecycle;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local fault-injecting JSON-RPC proxy in front of a LOCAL anvil (simulation only). */
public final class FaultProxy implements AutoCloseable {
    public enum Mode { PASS, HOLD_SEND, NULL_LOOKUPS, HTTP_429 }
    public volatile Mode mode = Mode.PASS;
    public final AtomicInteger sendRawCount = new AtomicInteger();
    private final HttpServer server;
    private final String upstream;
    private final HttpClient fwd = HttpClient.newHttpClient();

    public FaultProxy(String upstream) throws Exception {
        this.upstream = upstream;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            try {
                String body = new String(ex.getRequestBody().readAllBytes());
                boolean isSend = body.contains("eth_sendRawTransaction");
                boolean isLookup = body.contains("eth_getTransactionByHash") || body.contains("eth_getTransactionReceipt");
                if (isSend) sendRawCount.incrementAndGet();
                if (mode == Mode.HTTP_429) { ex.getResponseHeaders().add("Retry-After", "1"); byte[] h = "<!doctype html><title>429</title>".getBytes(); ex.getResponseHeaders().add("content-type", "text/html"); ex.sendResponseHeaders(429, h.length); ex.getResponseBody().write(h); ex.close(); return; }
                if (mode == Mode.NULL_LOOKUPS && isLookup) {
                    Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(body);
                    byte[] out = ("{\"jsonrpc\":\"2.0\",\"id\":" + (m.find() ? m.group(1) : "1") + ",\"result\":null}").getBytes();
                    ex.getResponseHeaders().add("content-type", "application/json"); ex.sendResponseHeaders(200, out.length); ex.getResponseBody().write(out); ex.close(); return;
                }
                HttpResponse<byte[]> up = fwd.send(HttpRequest.newBuilder(URI.create(upstream)).header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
                if (isSend && mode == Mode.HOLD_SEND) Thread.sleep(3500);                     // node processed it; the client will time out
                ex.getResponseHeaders().add("content-type", "application/json");
                ex.sendResponseHeaders(200, up.body().length); ex.getResponseBody().write(up.body()); ex.close();
            } catch (Exception e) { try { ex.close(); } catch (Exception ignore) { } }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());   // a held response must not block other requests
        server.start();
    }

    public String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    @Override public void close() { server.stop(0); }
}
