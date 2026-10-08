package com.marketplace.backend.scheduler;

import com.marketplace.backend.entity.ContractStatus;
import com.marketplace.backend.repository.WorkContractRepository;
import com.marketplace.backend.service.PendingFundingTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingFundingTimeoutScheduler {
    private final WorkContractRepository contracts;
    private final PendingFundingTimeoutService service;

    @Scheduled(initialDelayString = "${funding.timeout-initial-delay-ms:30000}",
            fixedDelayString = "${funding.timeout-interval-ms:60000}")
    public void scan() {
        for (var contract : contracts.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                ContractStatus.PENDING_FUNDING, LocalDateTime.now().minusHours(24))) {
            try { service.process(contract.getId()); }
            catch (RuntimeException ex) { log.warn("Funding deadline reconciliation will retry {}", contract.getId()); }
        }
    }
}
