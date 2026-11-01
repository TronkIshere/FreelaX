package com.freelax.avalanchegateway.chain;

import okhttp3.Interceptor;
import okhttp3.Response;

import java.io.IOException;

/**
 * Reads Retry-After from HTTP 429/503 answers (web3j does not expose headers) and makes the pacer wait. The failing call
 * itself still fails as a non-definitive transport error; only the following calls are delayed.
 */
public class RetryAfterInterceptor implements Interceptor {

    private static final long DEFAULT_BACKOFF_MILLIS = 5_000;

    private final RpcPacer pacer;

    public RetryAfterInterceptor(RpcPacer pacer) {
        this.pacer = pacer;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        if (response.code() == 429 || response.code() == 503) {
            pacer.backoff(parseSeconds(response.header("Retry-After")));
        }
        return response;
    }

    static long parseSeconds(String header) {
        if (header == null) {
            return DEFAULT_BACKOFF_MILLIS;
        }
        try {
            return Math.max(0, Long.parseLong(header.trim())) * 1_000;
        } catch (NumberFormatException e) {
            return DEFAULT_BACKOFF_MILLIS;
        }
    }
}
