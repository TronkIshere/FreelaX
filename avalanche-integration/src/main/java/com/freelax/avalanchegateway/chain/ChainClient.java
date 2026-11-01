package com.freelax.avalanchegateway.chain;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Response;

import java.io.IOException;
import java.util.function.Function;

/** Read-only identity calls only (eth_chainId, web3_clientVersion). Submission arrives in a later milestone. */
public class ChainClient {

    private final Web3j web3j;
    private final RpcPacer pacer;

    public ChainClient(Web3j web3j, RpcPacer pacer) {
        this.web3j = web3j;
        this.pacer = pacer;
    }

    public long chainId() {
        return call("eth_chainId", () -> web3j.ethChainId().send(), r -> r.getChainId().longValueExact());
    }

    public String clientVersion() {
        return call("web3_clientVersion", () -> web3j.web3ClientVersion().send(), r -> r.getWeb3ClientVersion());
    }

    public void shutdown() {
        web3j.shutdown();
    }

    private <R extends Response<?>, T> T call(String method, IoCall<R> request, Function<R, T> extract) {
        pacer.await();
        R response;
        try {
            response = request.get();
        } catch (IOException | RuntimeException e) {
            // web3j reports non-2xx answers (for example an HTTP 429 with an HTML body) as an unchecked
            // ClientConnectionException whose message embeds the body. Only the class name is kept.
            throw new ChainClientException(method + " failed: " + e.getClass().getSimpleName(), e);
        }
        if (response.hasError()) {
            throw new ChainClientException(method + " returned an RPC error");
        }
        return extract.apply(response);
    }

    @FunctionalInterface
    private interface IoCall<R> {
        R get() throws IOException;
    }
}
