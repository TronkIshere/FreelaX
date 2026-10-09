package com.marketplace.backend.scheduler;

import com.marketplace.backend.repository.ContractReviewRepository;
import com.marketplace.backend.repository.ContractSettlementRepository;
import com.marketplace.backend.repository.EscrowContractRepository;
import com.marketplace.backend.service.ContractReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContractReviewScheduler {
    private final ContractSettlementRepository settlements;
    private final EscrowContractRepository escrows;
    private final ContractReviewRepository reviews;
    private final ContractReviewService service;

    @Scheduled(initialDelayString = "${contract-review.initial-delay-ms:30000}",
            fixedDelayString = "${contract-review.interval-ms:60000}")
    public void reconcile() {
        for (UUID contractId : settlements.findUninvitedCompletedContractIds(PageRequest.of(0, 50))) {
            try { service.invite(contractId); }
            catch (RuntimeException ex) { log.warn("Review invitation requires another attempt: {}", contractId); }
        }
        for (UUID contractId : escrows.findUninvitedReleasedContractIds(PageRequest.of(0, 50))) {
            try { service.invite(contractId); }
            catch (RuntimeException ex) { log.warn("Escrow review invitation requires another attempt: {}", contractId); }
        }
        for (UUID contractId : reviews.findDuePublication(Instant.now().minus(14, ChronoUnit.DAYS), PageRequest.of(0, 50))) {
            try { service.publishDue(contractId); }
            catch (RuntimeException ex) { log.warn("Review publication requires another attempt: {}", contractId); }
        }
    }
}
