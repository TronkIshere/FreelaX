package com.payment.backend.service;

import com.payment.backend.entity.UnifiedFiatExitMock;
import com.payment.backend.entity.UnifiedMockStatement;
import com.payment.backend.repository.UnifiedFiatExitMockRepository;
import com.payment.backend.repository.UnifiedMockStatementRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UnifiedFiatExitMockServiceTest {
    private final UUID jobId = UUID.randomUUID();
    private final UUID contractId = UUID.randomUUID();
    private final UUID milestoneId = UUID.randomUUID();
    private final UnifiedFiatExitMockRepository exits = mock(UnifiedFiatExitMockRepository.class);
    private final UnifiedMockStatementRepository statements = mock(UnifiedMockStatementRepository.class);
    private final UnifiedFiatExitMockService service = new UnifiedFiatExitMockService(exits, statements);

    @Test
    void payoutLocksFeeBeneficiaryAndStatementExactlyOnce() {
        UUID flow = UUID.randomUUID();
        UnifiedFiatExitMockService.ExitRequest request = request(flow, "PAYOUT", "bank:123");
        final UnifiedFiatExitMock[] stored = new UnifiedFiatExitMock[1];
        List<UnifiedMockStatement> ledger = new ArrayList<>();
        when(exits.findWithLockByPaymentFlowId(flow)).thenAnswer(inv -> Optional.ofNullable(stored[0]));
        when(exits.saveAndFlush(any())).thenAnswer(inv -> { stored[0] = inv.getArgument(0); return stored[0]; });
        when(statements.saveAndFlush(any())).thenAnswer(inv -> {
            UnifiedMockStatement event = inv.getArgument(0); ledger.add(event); return event;
        });
        when(statements.findByPaymentFlowIdOrderByOccurredAtAsc(flow)).thenAnswer(inv -> ledger);

        var opened = service.request(request);
        assertThat(opened.feeUsdc()).isEqualByComparingTo("3.000000");
        assertThat(opened.payoutVnd()).isEqualByComparingTo("2425000");
        assertThat(service.request(request).payoutVnd()).isEqualByComparingTo("2425000");
        assertThatThrownBy(() -> service.request(request(flow, "PAYOUT", "bank:other")))
                .isInstanceOf(RuntimeException.class);
        service.settle(flow);
        service.settle(flow);
        assertThat(service.statement(flow).entries()).extracting(UnifiedFiatExitMockService.StatementRow::kind)
                .containsExactly("VND_PAYOUT", "PLATFORM_FEE");
        assertThat(ledger).hasSize(2);
        assertThat(ledger.get(0).getAmount()).isEqualByComparingTo("2425000");
        assertThat(ledger.get(1).getAmount()).isEqualByComparingTo("3.000000");
    }

    @Test
    void refundHasNoPlatformFeeOrVndPayout() {
        UUID flow = UUID.randomUUID();
        UnifiedFiatExitMockService.ExitRequest request = request(flow, "REFUND", "client:123");
        final UnifiedFiatExitMock[] stored = new UnifiedFiatExitMock[1];
        List<UnifiedMockStatement> ledger = new ArrayList<>();
        when(exits.findWithLockByPaymentFlowId(flow)).thenAnswer(inv -> Optional.ofNullable(stored[0]));
        when(exits.saveAndFlush(any())).thenAnswer(inv -> { stored[0] = inv.getArgument(0); return stored[0]; });
        when(statements.saveAndFlush(any())).thenAnswer(inv -> {
            UnifiedMockStatement event = inv.getArgument(0); ledger.add(event); return event;
        });
        when(statements.findByPaymentFlowIdOrderByOccurredAtAsc(flow)).thenAnswer(inv -> ledger);

        var opened = service.request(request);
        assertThat(opened.feeUsdc()).isEqualByComparingTo("0.000000");
        assertThat(opened.payoutVnd()).isEqualByComparingTo("0");
        service.settle(flow);
        assertThat(service.statement(flow).entries()).extracting(UnifiedFiatExitMockService.StatementRow::kind)
                .containsExactly("USD_REFUND");
        assertThat(ledger.get(0).getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void retryAfterProviderAlreadyPaidReturnsTheSettledOrderWithoutPayingAgain() {
        UUID flow = UUID.randomUUID();
        UnifiedFiatExitMockService.ExitRequest request = request(flow, "PAYOUT", "bank:123");
        final UnifiedFiatExitMock[] stored = new UnifiedFiatExitMock[1];
        List<UnifiedMockStatement> ledger = new ArrayList<>();
        when(exits.findWithLockByPaymentFlowId(flow)).thenAnswer(inv -> Optional.ofNullable(stored[0]));
        when(exits.saveAndFlush(any())).thenAnswer(inv -> { stored[0] = inv.getArgument(0); return stored[0]; });
        when(statements.saveAndFlush(any())).thenAnswer(inv -> {
            UnifiedMockStatement event = inv.getArgument(0); ledger.add(event); return event;
        });

        service.request(request);
        service.settle(flow);
        // Marketplace lost the response and retries with the same idempotency key.
        var retried = service.request(request);

        assertThat(retried.status()).isEqualTo("CONFIRMED");
        assertThat(ledger).hasSize(2);
        verify(exits, times(1)).saveAndFlush(any());
        assertThatThrownBy(() -> service.request(new UnifiedFiatExitMockService.ExitRequest(flow, jobId,
                contractId, milestoneId, "PAYOUT", "exit-" + flow, "withdrawal-" + flow, "bank:123",
                new BigDecimal("101.000000"), new BigDecimal("101.00"))))
                .isInstanceOf(RuntimeException.class);
        assertThat(ledger).hasSize(2);
    }

    @Test
    void feeAndVndRoundHalfUpOnTheLockedQuote() {
        UUID flow = UUID.randomUUID();
        when(exits.findWithLockByPaymentFlowId(flow)).thenReturn(Optional.empty());
        when(exits.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var opened = service.request(new UnifiedFiatExitMockService.ExitRequest(flow, jobId, contractId,
                milestoneId, "PAYOUT", "exit-" + flow, "withdrawal-" + flow, "bank:123",
                new BigDecimal("33.330000"), new BigDecimal("33.33")));

        assertThat(opened.feeUsdc()).isEqualByComparingTo("1.000000");
        assertThat(opened.payoutVnd()).isEqualByComparingTo("808250");
    }

    private UnifiedFiatExitMockService.ExitRequest request(UUID flow, String kind, String beneficiary) {
        return new UnifiedFiatExitMockService.ExitRequest(flow, jobId, contractId,
                milestoneId, kind, "exit-" + flow, "withdrawal-" + flow, beneficiary,
                new BigDecimal("100.000000"), new BigDecimal("100.00"));
    }
}
