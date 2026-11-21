package com.marketplace.backend.scheduler;

import com.marketplace.backend.entity.AvalanchePayoutRequest;
import com.marketplace.backend.entity.AvalanchePayoutStatus;
import com.marketplace.backend.repository.AvalanchePayoutRequestRepository;
import com.marketplace.backend.service.AvalanchePayoutService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * Owner request 2026-11-21: advances a freelancer's VND payout without the manual "Làm mới chi trả" click. It runs exactly the
 * freelancer's own refresh (on-chain transfer check, then the mock partner's fiat status) for payouts still in progress; it never
 * sends a transaction, moves funds or changes the partner. Flag {@code avalanche-payout.auto-refresh} (false = manual only).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@ConditionalOnProperty(name = "avalanche-payout.auto-refresh", havingValue = "true")
public class AvalanchePayoutRefreshScheduler {

    static final EnumSet<AvalanchePayoutStatus> IN_PROGRESS = EnumSet.of(AvalanchePayoutStatus.TRANSFER_SUBMITTED,
            AvalanchePayoutStatus.TRANSFER_CONFIRMED, AvalanchePayoutStatus.FIAT_PAYOUT_PENDING);

    AvalanchePayoutRequestRepository payouts;
    AvalanchePayoutService payoutService;

    @Scheduled(initialDelayString = "${avalanche-payout.auto-refresh-initial-delay-ms:15000}",
            fixedDelayString = "${avalanche-payout.auto-refresh-interval-ms:10000}")
    public void refreshInProgress() {
        for (AvalanchePayoutRequest p : payouts.findTop20ByStatusInOrderByUpdatedAtAsc(IN_PROGRESS)) {
            try {
                payoutService.refresh(p.getFreelancerUserId(), p.getId());
            } catch (Exception e) {
                log.warn("Avalanche payout {} will be refreshed again: {}", p.getId(), e.getMessage());
            }
        }
    }
}
