package com.freelax.avalanchegateway.tx;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Direct JSON-RPC to the local Anvil for test control (funding, automine, mining, code). LOCAL_SIMULATION only. */
final class AnvilRpc {

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
    private static final Pattern RESULT_HEX = Pattern.compile("\"result\":\"0x([0-9a-fA-F]*)\"");

    private AnvilRpc() {
    }

    /** @param jsonParams already-serialised JSON values, for example {@code "\"0xabc\""} or {@code "false"} */
    static String call(String url, String method, String... jsonParams) {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":[" + String.join(",", jsonParams) + "]}";
        java.io.IOException last = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
                if (response.body().contains("\"error\"")) {
                    throw new IllegalStateException(method + " failed: " + response.body());
                }
                return response.body();
            } catch (java.io.IOException e) {
                // Docker Desktop's published port can accept and drop connections for a moment after the container starts
                last = e;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("no usable response from " + url + " after retries", last);
    }

    static void fund(String url, String address) {
        call(url, "anvil_setBalance", "\"" + address + "\"", "\"0x56bc75e2d63100000\"");
    }

    static void setCode(String url, String address, String code) {
        call(url, "anvil_setCode", "\"" + address + "\"", "\"" + code + "\"");
    }

    static void setAutomine(String url, boolean on) {
        call(url, "evm_setAutomine", Boolean.toString(on));
    }

    static void mine(String url) {
        call(url, "evm_mine");
    }

    static long transactionCount(String url, String address, String tag) {
        Matcher m = RESULT_HEX.matcher(call(url, "eth_getTransactionCount", "\"" + address + "\"", "\"" + tag + "\""));
        if (!m.find()) {
            throw new IllegalStateException("no result");
        }
        return Long.parseLong(m.group(1), 16);
    }
}
