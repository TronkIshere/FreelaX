package com.payment.backend.service;

import com.payment.backend.repository.UnifiedUsdOrderMockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class UnifiedUsdOrderMockScheduler {
    private final UnifiedUsdOrderMockRepository orders;
    private final UnifiedUsdOrderMockService service;

    @Scheduled(initialDelayString = "${unified-mock.confirm-initial-delay-ms:5000}",
            fixedDelayString = "${unified-mock.confirm-interval-ms:5000}")
    public void confirmPending() {
        for (var row : orders.findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                "PENDING", Instant.now().minusSeconds(2))) {
            try { service.confirmUsd(row.getPaymentFlowId()); }
            catch (RuntimeException ex) { log.warn("Unified USD confirmation will retry for {}", row.getPaymentFlowId()); }
        }
    }
}
