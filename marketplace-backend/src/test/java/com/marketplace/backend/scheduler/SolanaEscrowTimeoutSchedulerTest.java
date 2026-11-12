package com.marketplace.backend.scheduler;

import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.repository.EscrowContractRepository;
import com.marketplace.backend.service.SolanaEscrowTimeoutService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** R13 (owner decision 2026-11-12, option a): the Solana escrow scheduler is idle while the Solana rail is disabled. */
class SolanaEscrowTimeoutSchedulerTest {

    @Test
    void disabledRailMeansNoPollingAndNoReconciliation() {
        EscrowContractRepository escrows = mock(EscrowContractRepository.class);
        SolanaEscrowTimeoutService service = mock(SolanaEscrowTimeoutService.class);
        SolanaCprProperties solana = new SolanaCprProperties();
        solana.setEnabled(false);
        new SolanaEscrowTimeoutScheduler(escrows, service, solana).process();
        verifyNoInteractions(escrows, service);
    }

    @Test
    void enabledRailPollsAsBefore() {
        EscrowContractRepository escrows = mock(EscrowContractRepository.class);
        SolanaEscrowTimeoutService service = mock(SolanaEscrowTimeoutService.class);
        when(escrows.findTop100ByLastChainStatusInOrderByUpdatedAtAsc(anyCollection())).thenReturn(List.of());
        when(escrows.findUnifiedTerminalAwaitingFlow(any())).thenReturn(List.of());
        new SolanaEscrowTimeoutScheduler(escrows, service, new SolanaCprProperties()).process();
        verify(escrows).findTop100ByLastChainStatusInOrderByUpdatedAtAsc(anyCollection());
        verifyNoInteractions(service);
    }
}
