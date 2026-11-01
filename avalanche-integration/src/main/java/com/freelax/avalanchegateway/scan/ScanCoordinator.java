package com.freelax.avalanchegateway.scan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs scans one at a time. A hint (from the WebSocket) only asks for a scan sooner; it carries no data and sets no state.
 * Hints that arrive while a scan is queued or running are coalesced into one further pass, and the periodic pass guarantees
 * progress when no hint ever arrives.
 */
public class ScanCoordinator implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ScanCoordinator.class);

    private final LogScanner scanner;
    private final long intervalMs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "avalanche-log-scanner");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean queued = new AtomicBoolean();
    private final AtomicLong passes = new AtomicLong();
    private volatile LogScanner.ScanResult lastResult;
    private ScheduledExecutorService periodic;

    public ScanCoordinator(LogScanner scanner, long intervalMs) {
        this.scanner = scanner;
        this.intervalMs = intervalMs;
    }

    public synchronized void start() {
        if (intervalMs <= 0 || periodic != null) {
            return;
        }
        periodic = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "avalanche-log-scan-timer");
            thread.setDaemon(true);
            return thread;
        });
        periodic.scheduleWithFixedDelay(this::hint, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    /** Requests a pass; never blocks and never carries data. */
    public void hint() {
        if (queued.compareAndSet(false, true)) {
            worker.execute(() -> {
                queued.set(false);
                try {
                    lastResult = scanner.scanOnce();
                } catch (RuntimeException e) {
                    log.warn("log scan pass failed: {}", e.getClass().getSimpleName());
                } finally {
                    passes.incrementAndGet();
                }
            });
        }
    }

    public long passes() {
        return passes.get();
    }

    public LogScanner.ScanResult lastResult() {
        return lastResult;
    }

    @Override
    public synchronized void close() {
        if (periodic != null) {
            periodic.shutdownNow();
            periodic = null;
        }
        worker.shutdownNow();
    }
}
