package com.payment.backend.service;

import com.payment.backend.repository.UnifiedFiatExitMockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class UnifiedFiatExitMockScheduler {
    private final UnifiedFiatExitMockRepository exits;
    private final UnifiedFiatExitMockService service;

    @Scheduled(fixedDelayString = "${unified-mock.exit-poll-ms:2000}")
    public void settle() {
        exits.findTop50ByStatusOrderByCreatedAtAsc("PENDING").stream()
                .filter(row -> row.getCreatedAt().plusSeconds(2).isBefore(Instant.now()))
                .forEach(row -> service.settle(row.getPaymentFlowId()));
    }
}
