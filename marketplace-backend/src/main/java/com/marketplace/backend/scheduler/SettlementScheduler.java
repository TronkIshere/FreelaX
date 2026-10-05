package com.marketplace.backend.scheduler;

import com.marketplace.backend.repository.*;
import com.marketplace.backend.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {
    private final MilestoneRepository milestones;
    private final ContractSettlementRepository settlements;
    private final SettlementService service;

    @Scheduled(initialDelayString = "${settlement.initial-delay-ms:30000}",
            fixedDelayString = "${settlement.interval-ms:30000}")
    public void reconcile() {
        for (UUID id : milestones.findUnsettledReleaseIds(PageRequest.of(0, 50))) {
            try {
                service.prepare(id);
            } catch (Exception ex) {
                // IDs only: exception/provider bodies can contain private details.
                log.warn("Settlement eligibility/persistence requires attention for milestone {}", id);
            }
        }
        for (UUID id : settlements.findDueIds(Instant.now(), PageRequest.of(0, 50))) {
            try {
                service.process(id);
            } catch (Exception ex) {
                log.warn("Settlement processing will reconcile after transaction failure: {}", id);
            }
        }
    }
}
