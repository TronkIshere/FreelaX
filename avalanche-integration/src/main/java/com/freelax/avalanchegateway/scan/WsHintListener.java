package com.freelax.avalanchegateway.scan;

import io.reactivex.disposables.Disposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.websocket.WebSocketService;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Subscribes to newHeads as a LATENCY HINT only: each notification asks the coordinator for a scan sooner. It never carries
 * events and never sets any state; a resubscription does not replay missed blocks (observed on Fuji), so every (re)connect also
 * asks for a pass, and the persisted cursor recovers the gap with bounded reads. The design never depends on the socket staying up.
 */
public class WsHintListener implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WsHintListener.class);

    private final String wsUrl;
    private final ScanCoordinator coordinator;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong hints = new AtomicLong();
    private final AtomicLong connections = new AtomicLong();
    private Thread thread;

    public WsHintListener(String wsUrl, ScanCoordinator coordinator) {
        this.wsUrl = wsUrl;
        this.coordinator = coordinator;
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        thread = new Thread(this::loop, "avalanche-ws-hints");
        thread.setDaemon(true);
        thread.start();
    }

    public long hints() {
        return hints.get();
    }

    public long connections() {
        return connections.get();
    }

    private void loop() {
        long backoffMs = 1_000;
        while (running.get()) {
            AtomicBoolean closed = new AtomicBoolean();
            WebSocketService service = null;
            Disposable subscription = null;
            try {
                service = new WebSocketService(wsUrl, false);
                service.connect(message -> { }, error -> closed.set(true), () -> closed.set(true));
                Web3j web3j = Web3j.build(service);
                subscription = web3j.newHeadsNotifications().subscribe(notification -> {
                    hints.incrementAndGet();
                    coordinator.hint();
                }, error -> closed.set(true));
                connections.incrementAndGet();
                backoffMs = 1_000;
                coordinator.hint();
                while (running.get() && !closed.get()) {
                    Thread.sleep(250);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("WebSocket hint connection failed: {}", e.getClass().getSimpleName());
            } finally {
                if (subscription != null) {
                    subscription.dispose();
                }
                if (service != null) {
                    try {
                        service.close();
                    } catch (RuntimeException ignored) {
                        // closing a dead socket is best effort
                    }
                }
            }
            if (!running.get()) {
                return;
            }
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            backoffMs = Math.min(backoffMs * 2, 30_000);
        }
    }

    @Override
    public synchronized void close() {
        running.set(false);
        if (thread != null) {
            thread.interrupt();
        }
    }
}
