package com.freelax.avalanchegateway.tx;

import com.freelax.avalanchegateway.service.TransactionReconcileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Optional periodic reconciliation pass (interval 0 disables it). One pass at a time, errors never stop the loop. */
public class TxReconcileScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TxReconcileScheduler.class);

    private final TransactionReconcileService reconciler;
    private final long intervalMs;
    private ScheduledExecutorService executor;

    public TxReconcileScheduler(TransactionReconcileService reconciler, long intervalMs) {
        this.reconciler = reconciler;
        this.intervalMs = intervalMs;
    }

    public synchronized void start() {
        if (intervalMs <= 0 || executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "avalanche-tx-reconciler");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try {
                reconciler.reconcileInFlight();
            } catch (RuntimeException e) {
                log.warn("reconciliation pass failed: {}", e.getClass().getSimpleName());
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void close() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
