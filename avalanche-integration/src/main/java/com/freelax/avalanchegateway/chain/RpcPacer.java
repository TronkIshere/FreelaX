package com.freelax.avalanchegateway.chain;

/** Enforces a minimum gap between requests to one RPC host so public endpoints are not hammered. */
public class RpcPacer {

    private final long minGapMillis;
    private long lastRequestNanos;
    private boolean used;

    public RpcPacer(long minGapMillis) {
        this.minGapMillis = minGapMillis;
    }

    public synchronized void await() {
        if (used && minGapMillis > 0) {
            long waitMillis = minGapMillis - (System.nanoTime() - lastRequestNanos) / 1_000_000;
            if (waitMillis > 0) {
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ChainClientException("interrupted while pacing RPC requests", e);
                }
            }
        }
        used = true;
        lastRequestNanos = System.nanoTime();
    }
}
