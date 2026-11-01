package com.freelax.avalanchegateway.chain;

import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.env.EnvironmentGuard;
import okhttp3.OkHttpClient;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.time.Duration;

public final class ChainClients {

    private ChainClients() {
    }

    /** The endpoint pre-check runs first, so a rejected endpoint is never contacted. */
    public static ChainClient create(AvalancheEnvironment environment, String rpcUrl, int timeoutMs, int minGapMs) {
        EnvironmentGuard.checkEndpoint(environment, rpcUrl);
        RpcPacer pacer = new RpcPacer(minGapMs);
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .readTimeout(Duration.ofMillis(timeoutMs))
                .callTimeout(Duration.ofMillis(timeoutMs * 2L))
                .addInterceptor(new RetryAfterInterceptor(pacer))
                .build();
        return new ChainClient(Web3j.build(new HttpService(rpcUrl.trim(), http)), pacer);
    }
}
