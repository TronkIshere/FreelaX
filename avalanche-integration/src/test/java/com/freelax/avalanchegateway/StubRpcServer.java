package com.freelax.avalanchegateway;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loopback JSON-RPC stub answering only eth_chainId and web3_clientVersion; records every request it receives. */
public final class StubRpcServer implements AutoCloseable {

    private static final Pattern METHOD = Pattern.compile("\"method\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    private final HttpServer server;
    private final List<String> methods = new CopyOnWriteArrayList<>();

    private StubRpcServer(HttpServer server) {
        this.server = server;
    }

    public static StubRpcServer start(long chainId, String clientVersion) throws IOException {
        return start(chainId, clientVersion, false);
    }

    public static StubRpcServer start(long chainId, String clientVersion, boolean rateLimited) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        StubRpcServer stub = new StubRpcServer(http);
        http.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Matcher method = METHOD.matcher(body);
            Matcher id = ID.matcher(body);
            String name = method.find() ? method.group(1) : "";
            stub.methods.add(name);
            String response;
            int status = 200;
            String contentType = "application/json";
            if (rateLimited) {
                status = 429;
                contentType = "text/html";
                response = "<html><body>Too Many Requests</body></html>";
                exchange.getResponseHeaders().add("Retry-After", "16");
            } else {
                String requestId = id.find() ? id.group(1) : "1";
                String result = switch (name) {
                    case "eth_chainId" -> "\"0x" + Long.toHexString(chainId) + "\"";
                    case "web3_clientVersion" -> "\"" + clientVersion + "\"";
                    default -> "null";
                };
                response = "{\"jsonrpc\":\"2.0\",\"id\":" + requestId + ",\"result\":" + result + "}";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        http.start();
        return stub;
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<String> methods() {
        return methods;
    }

    public int requestCount() {
        return methods.size();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
