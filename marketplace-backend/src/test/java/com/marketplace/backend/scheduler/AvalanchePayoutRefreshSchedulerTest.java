package com.marketplace.backend.scheduler;

import com.marketplace.backend.entity.AvalanchePayoutRequest;
import com.marketplace.backend.entity.AvalanchePayoutStatus;
import com.marketplace.backend.repository.AvalanchePayoutRequestRepository;
import com.marketplace.backend.service.AvalanchePayoutService;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Owner request 2026-11-21: in-progress VND payouts are refreshed automatically with the freelancer's own refresh. */
class AvalanchePayoutRefreshSchedulerTest {
    private final AvalanchePayoutRequestRepository payouts = mock(AvalanchePayoutRequestRepository.class);
    private final AvalanchePayoutService service = mock(AvalanchePayoutService.class);
    private final AvalanchePayoutRefreshScheduler scheduler = new AvalanchePayoutRefreshScheduler(payouts, service);

    @Test
    void refreshesOnlyInProgressPayoutsAsTheirOwnFreelancerAndContinuesAfterAFailure() {
        AvalanchePayoutRequest first = payout();
        AvalanchePayoutRequest second = payout();
        when(payouts.findTop20ByStatusInOrderByUpdatedAtAsc(AvalanchePayoutRefreshScheduler.IN_PROGRESS))
                .thenReturn(List.of(first, second));
        UUID owner = first.getFreelancerUserId(), id = first.getId();
        when(service.refresh(owner, id)).thenThrow(new IllegalStateException("gateway down"));

        scheduler.refreshInProgress();

        UUID firstOwner = first.getFreelancerUserId(), firstId = first.getId();
        UUID secondOwner = second.getFreelancerUserId(), secondId = second.getId();
        verify(service).refresh(eq(firstOwner), eq(firstId));
        verify(service).refresh(eq(secondOwner), eq(secondId));
        assertThat(AvalanchePayoutRefreshScheduler.IN_PROGRESS).isEqualTo(EnumSet.of(AvalanchePayoutStatus.TRANSFER_SUBMITTED,
                AvalanchePayoutStatus.TRANSFER_CONFIRMED, AvalanchePayoutStatus.FIAT_PAYOUT_PENDING));
    }

    private AvalanchePayoutRequest payout() {
        AvalanchePayoutRequest p = mock(AvalanchePayoutRequest.class);
        UUID id = UUID.randomUUID();
        UUID freelancer = UUID.randomUUID();
        when(p.getId()).thenReturn(id);
        when(p.getFreelancerUserId()).thenReturn(freelancer);
        return p;
    }
}
