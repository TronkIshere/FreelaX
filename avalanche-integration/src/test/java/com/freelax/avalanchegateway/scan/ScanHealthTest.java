package com.freelax.avalanchegateway.scan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A6 / F6: scanner health becomes DEGRADED after repeated failures and recovers on the next successful pass. */
class ScanHealthTest {

    private static void awaitPasses(ScanCoordinator c, long n) throws InterruptedException {
        for (int i = 0; i < 200 && c.passes() < n; i++) {
            Thread.sleep(10);
        }
        assertEquals(n, c.passes());
    }

    @Test
    void repeatedFailuresDegradeAndASuccessRecovers() throws Exception {
        LogScanner scanner = mock(LogScanner.class);
        when(scanner.scanOnce())
                .thenThrow(new IllegalStateException("rpc down"))
                .thenThrow(new IllegalStateException("rpc down"))
                .thenThrow(new IllegalStateException("rpc down"))
                .thenReturn(new LogScanner.ScanResult(1, 10, 0, 0, 0, 10, 1));
        try (ScanCoordinator c = new ScanCoordinator(scanner, 0)) {
            assertEquals("STARTING", c.health().state());
            for (int pass = 1; pass <= 3; pass++) {
                c.hint();
                awaitPasses(c, pass);
            }
            ScanCoordinator.Health degraded = c.health();
            assertEquals("DEGRADED", degraded.state());
            assertEquals(3, degraded.consecutiveFailures());
            assertEquals("IllegalStateException", degraded.lastErrorClass(), "the class only, never the message");
            assertNull(degraded.lastSuccessAtMillis());

            c.hint();
            awaitPasses(c, 4);
            ScanCoordinator.Health ok = c.health();
            assertEquals("OK", ok.state());
            assertEquals(0, ok.consecutiveFailures());
            assertNull(ok.lastErrorClass());
            assertNotNull(ok.lastSuccessAtMillis());
            assertEquals(10L, ok.lastCursor());
        }
    }
}
