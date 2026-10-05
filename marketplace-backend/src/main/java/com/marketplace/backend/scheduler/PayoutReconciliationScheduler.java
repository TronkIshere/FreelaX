package com.marketplace.backend.scheduler;

import com.marketplace.backend.service.PayoutService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PayoutReconciliationScheduler {

    PayoutService payoutService;

    @Scheduled(initialDelayString = "${payout.reconcile-initial-delay-ms:30000}",
            fixedDelayString = "${payout.reconcile-interval-ms:30000}")
    public void reconcilePendingPayouts() {
        for (UUID recordId : payoutService.findRecordIdsToReconcile()) {
            try {
                payoutService.reconcile(recordId);
            } catch (Exception e) {
                log.warn("Payout reconciliation will retry for record {}", recordId);
            }
        }
    }
}
