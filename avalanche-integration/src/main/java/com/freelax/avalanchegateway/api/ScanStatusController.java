package com.freelax.avalanchegateway.api;

import com.freelax.avalanchegateway.scan.LogScanner;
import com.freelax.avalanchegateway.scan.ScanCoordinator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Register items A6 / F6: scanner metrics and alerting for monitoring (read-only). The state is DISABLED when no scanner is
 * configured, STARTING before the first pass, OK, or DEGRADED after repeated failed passes (also logged at ERROR once).
 * Lag is the number of blocks between the persisted cursor and the head the scanner may read; a lag read failure is reported
 * as null, never as zero.
 */
@RestController
@RequestMapping("/api/v1/avalanche/scan")
public class ScanStatusController {

    public record ScanStatus(String state, long passes, int consecutiveFailures, String lastErrorClass, Long lastSuccessAtMillis,
                             Long cursor, Long safeHead, Long lagBlocks) {
    }

    private final ObjectProvider<ScanCoordinator> coordinator;
    private final ObjectProvider<LogScanner> scanner;

    public ScanStatusController(ObjectProvider<ScanCoordinator> coordinator, ObjectProvider<LogScanner> scanner) {
        this.coordinator = coordinator;
        this.scanner = scanner;
    }

    @GetMapping("/status")
    public ScanStatus status() {
        ScanCoordinator c = coordinator.getIfAvailable();
        LogScanner s = scanner.getIfAvailable();
        if (c == null || s == null) {
            return new ScanStatus("DISABLED", 0, 0, null, null, null, null, null);
        }
        ScanCoordinator.Health h = c.health();
        Long cursor = null;
        Long head = null;
        try {
            cursor = s.cursorNow().orElse(null);
            head = s.safeHeadNow();
        } catch (RuntimeException e) {
            // unknown, not zero
        }
        Long lag = cursor == null || head == null ? null : Math.max(0, head - cursor);
        return new ScanStatus(h.state(), h.passes(), h.consecutiveFailures(), h.lastErrorClass(), h.lastSuccessAtMillis(), cursor, head, lag);
    }
}
