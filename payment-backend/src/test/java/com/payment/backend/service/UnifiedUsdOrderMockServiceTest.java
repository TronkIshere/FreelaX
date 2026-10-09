package com.payment.backend.service;

import com.payment.backend.entity.UnifiedMockStatement;
import com.payment.backend.entity.UnifiedUsdOrderMock;
import com.payment.backend.exception.ApplicationException;
import com.payment.backend.repository.UnifiedMockStatementRepository;
import com.payment.backend.repository.UnifiedUsdOrderMockRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UnifiedUsdOrderMockServiceTest {
    private final UnifiedUsdOrderMockRepository orders = mock(UnifiedUsdOrderMockRepository.class);
    private final UnifiedMockStatementRepository statements = mock(UnifiedMockStatementRepository.class);
    private final UnifiedUsdOrderMockService service = new UnifiedUsdOrderMockService(orders, statements);

    private final UUID flowId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID contractId = UUID.randomUUID();
    private final UUID milestoneId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    private UnifiedUsdOrderMockService.OpenRequest request(String key) {
        return new UnifiedUsdOrderMockService.OpenRequest(flowId, jobId, contractId,
                milestoneId, clientId, new BigDecimal("100.00"), new BigDecimal("100.000000"),
                "VIETCOMBANK", "123456789", "CLIENT NAME", key);
    }

    @Test
    void orderIsStableAcrossRetryAndDoesNotConfirmUsdFromOpen() {
        when(orders.saveAndFlush(any(UnifiedUsdOrderMock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var first = service.open(request("same-key"));
        assertThat(first.status()).isEqualTo("AWAITING_CLIENT");
        assertThat(first.escrowUsdc()).isEqualByComparingTo("100.000000");
        verifyNoInteractions(statements);

        UnifiedUsdOrderMock persisted = mock(UnifiedUsdOrderMock.class);
        when(persisted.getPaymentFlowId()).thenReturn(flowId);
        when(persisted.getJobId()).thenReturn(jobId);
        when(persisted.getContractId()).thenReturn(contractId);
        when(persisted.getMilestoneId()).thenReturn(milestoneId);
        when(persisted.getClientId()).thenReturn(clientId);
        when(persisted.getGrossUsd()).thenReturn(new BigDecimal("100.00"));
        when(persisted.getEscrowUsdc()).thenReturn(new BigDecimal("100.000000"));
        when(persisted.getPayerBankCode()).thenReturn("VIETCOMBANK");
        when(persisted.getPayerBankAccountNumber()).thenReturn("123456789");
        when(persisted.getPayerBankAccountHolderName()).thenReturn("CLIENT NAME");
        when(persisted.getFundKey()).thenReturn("same-key");
        when(persisted.getStatus()).thenReturn("AWAITING_CLIENT");
        when(orders.findWithLockByPaymentFlowId(flowId)).thenReturn(Optional.of(persisted));

        assertThat(service.open(request("same-key")).status()).isEqualTo("AWAITING_CLIENT");
        assertThatThrownBy(() -> service.open(request("different-key")))
                .isInstanceOf(ApplicationException.class);
        verify(orders, times(1)).saveAndFlush(any());
    }

    @Test
    void schedulerConfirmationCreatesOneIndependentUsdStatement() {
        UnifiedUsdOrderMock row = new UnifiedUsdOrderMock();
        row.setPaymentFlowId(flowId);
        row.setJobId(jobId);
        row.setContractId(contractId);
        row.setMilestoneId(milestoneId);
        row.setClientId(clientId);
        row.setGrossUsd(new BigDecimal("100.00"));
        row.setEscrowUsdc(new BigDecimal("100.000000"));
        row.setFundKey("same-key");
        row.setQuoteId("quote-1");
        row.setQuoteExpiresAt(Instant.now().plusSeconds(600));
        row.setStatus("PENDING");
        when(orders.findWithLockByPaymentFlowId(flowId)).thenReturn(Optional.of(row));
        when(statements.saveAndFlush(any(UnifiedMockStatement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.confirmUsd(flowId).status()).isEqualTo("USD_RECEIVED");
        assertThat(service.confirmUsd(flowId).status()).isEqualTo("USD_RECEIVED");
        verify(statements, times(1)).saveAndFlush(argThat(event ->
                event.getPaymentFlowId().equals(flowId)
                        && "USD_RECEIVED".equals(event.getKind())
                        && event.getAmount().compareTo(new BigDecimal("100.00")) == 0));
    }

    @Test
    void clientSubmitStartsMockPaymentWithoutInventingUsdReceipt() {
        UnifiedUsdOrderMock row = new UnifiedUsdOrderMock();
        row.setPaymentFlowId(flowId);
        row.setFundKey("same-key");
        row.setQuoteExpiresAt(Instant.now().plusSeconds(600));
        row.setStatus("AWAITING_CLIENT");
        when(orders.findWithLockByPaymentFlowId(flowId)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.submit(flowId, "other-key"))
                .isInstanceOf(ApplicationException.class);
        assertThat(service.submit(flowId, "same-key").status()).isEqualTo("PENDING");
        assertThat(service.submit(flowId, "same-key").status()).isEqualTo("PENDING");
        verifyNoInteractions(statements);
    }

    @Test
    void expiredQuoteDoesNotSimulateUsdReceipt() {
        UnifiedUsdOrderMock row = new UnifiedUsdOrderMock();
        row.setPaymentFlowId(flowId);
        row.setFundKey("same-key");
        row.setQuoteExpiresAt(Instant.now().minusSeconds(1));
        row.setStatus("AWAITING_CLIENT");
        when(orders.findWithLockByPaymentFlowId(flowId)).thenReturn(Optional.of(row));

        assertThat(service.submit(flowId, "same-key").status()).isEqualTo("EXPIRED");
        service.confirmUsd(flowId);
        verifyNoInteractions(statements);
    }

    @Test
    void amountMismatchIsRejectedBeforeCreatingOrder() {
        var bad = new UnifiedUsdOrderMockService.OpenRequest(flowId, jobId, contractId,
                milestoneId, clientId, new BigDecimal("100.00"), new BigDecimal("99.999999"),
                "VIETCOMBANK", "123456789", "CLIENT NAME", "key");
        assertThatThrownBy(() -> service.open(bad)).isInstanceOf(ApplicationException.class);
        verifyNoInteractions(orders, statements);
    }
}
