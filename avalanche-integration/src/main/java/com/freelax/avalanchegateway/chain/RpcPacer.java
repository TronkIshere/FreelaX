package com.freelax.avalanchegateway.chain;

import com.freelax.avalanchegateway.exception.ChainClientException;

/**
 * Enforces a minimum gap between requests to one RPC host and honours a server-requested pause (HTTP Retry-After), so
 * public endpoints are not hammered.
 */
public class RpcPacer {

    private static final long MAX_BACKOFF_MILLIS = 120_000;

    private final long minGapMillis;
    private long lastRequestNanos;
    private long notBeforeNanos;
    private boolean used;

    public RpcPacer(long minGapMillis) {
        this.minGapMillis = minGapMillis;
    }

    /** Called when the server asked us to wait (Retry-After); bounded so a hostile value cannot stall the gateway. */
    public synchronized void backoff(long millis) {
        long bounded = Math.max(0, Math.min(millis, MAX_BACKOFF_MILLIS));
        notBeforeNanos = Math.max(notBeforeNanos, System.nanoTime() + bounded * 1_000_000);
    }

    public synchronized void await() {
        long now = System.nanoTime();
        long waitMillis = 0;
        if (used && minGapMillis > 0) {
            waitMillis = minGapMillis - (now - lastRequestNanos) / 1_000_000;
        }
        waitMillis = Math.max(waitMillis, (notBeforeNanos - now) / 1_000_000);
        if (waitMillis > 0) {
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ChainClientException("interrupted while pacing RPC requests", e);
            }
        }
        used = true;
        lastRequestNanos = System.nanoTime();
    }
}
