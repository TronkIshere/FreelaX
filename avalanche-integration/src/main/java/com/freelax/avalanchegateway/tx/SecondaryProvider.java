package com.freelax.avalanchegateway.tx;

import com.freelax.avalanchegateway.chain.ChainClient;

/** Optional second provider of the SAME environment, used only to cross-check a lookup; never a fallback for submission. */
public record SecondaryProvider(ChainClient client) implements AutoCloseable {

    @Override
    public void close() {
        if (client != null) {
            client.shutdown();
        }
    }
}
