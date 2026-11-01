package com.freelax.avalanchegateway.chain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetryAfterInterceptorTest {

    @Test
    void numericSecondsAreHonouredAndAnythingElseGetsTheDefault() {
        assertEquals(16_000, RetryAfterInterceptor.parseSeconds("16"));
        assertEquals(60_000, RetryAfterInterceptor.parseSeconds(" 60 "));
        assertEquals(0, RetryAfterInterceptor.parseSeconds("0"));
        assertEquals(0, RetryAfterInterceptor.parseSeconds("-5"));
        assertEquals(5_000, RetryAfterInterceptor.parseSeconds(null));
        assertEquals(5_000, RetryAfterInterceptor.parseSeconds("Wed, 21 Oct 2026 07:28:00 GMT"));
    }

    @Test
    void aHostileRetryAfterIsBoundedByThePacer() {
        RpcPacer pacer = new RpcPacer(0);
        long start = System.nanoTime();
        pacer.backoff(0);
        pacer.await();
        assertEquals(true, (System.nanoTime() - start) / 1_000_000 < 1000);
    }
}
