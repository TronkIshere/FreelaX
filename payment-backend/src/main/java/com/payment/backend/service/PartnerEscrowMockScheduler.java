package com.payment.backend.service;

import com.payment.backend.repository.PartnerEscrowMockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class PartnerEscrowMockScheduler {
    private final PartnerEscrowMockRepository escrows;
    private final PartnerEscrowMockService service;

    @Scheduled(initialDelayString = "${partner-mock.confirm-initial-delay-ms:5000}",
            fixedDelayString = "${partner-mock.confirm-interval-ms:5000}")
    public void confirmPending() {
        for (var row : escrows.findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                "PENDING", Instant.now().minusSeconds(2))) {
            try { service.confirmFunding(row.getMilestoneId()); }
            catch (RuntimeException ex) { log.warn("Partner mock funding confirmation will retry for {}", row.getMilestoneId()); }
        }
    }
}
