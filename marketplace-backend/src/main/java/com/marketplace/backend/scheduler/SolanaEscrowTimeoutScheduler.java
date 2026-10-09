package com.marketplace.backend.scheduler;

import com.marketplace.backend.repository.EscrowContractRepository;
import com.marketplace.backend.service.SolanaEscrowTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SolanaEscrowTimeoutScheduler {
    private final EscrowContractRepository escrows;
    private final SolanaEscrowTimeoutService service;

    @Scheduled(initialDelayString = "${escrow.reconcile-initial-delay-ms:30000}",
            fixedDelayString = "${escrow.reconcile-interval-ms:30000}")
    public void process() {
        for (var escrow : escrows.findTop100ByLastChainStatusInOrderByUpdatedAtAsc(
                List.of("Funded", "Submitted", "Revision", "Disputed",
                        "PENDING_CONFIRMATION", "AWAITING_SIGNATURE", "MISMATCH",
                        "Released", "Refunded"))) {
            try {
                service.process(escrow.getId());
            } catch (RuntimeException ex) {
                log.warn("Escrow reconciliation will retry record {}", escrow.getId());
            }
        }
        // Safety net: a work-state path may mark the escrow reconciled before the unified
        // payment flow records USDC_RELEASE/USDC_REFUND; re-read the chain for those.
        for (var escrow : escrows.findUnifiedTerminalAwaitingFlow(org.springframework.data.domain.PageRequest.of(0, 50))) {
            try {
                service.process(escrow.getId());
            } catch (RuntimeException ex) {
                log.warn("Unified terminal reconciliation will retry record {}", escrow.getId());
            }
        }
    }
}
