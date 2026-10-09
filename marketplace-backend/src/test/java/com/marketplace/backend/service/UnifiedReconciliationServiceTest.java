package com.marketplace.backend.service;

import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.dto.response.solana.SolanaEscrowResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UnifiedReconciliationServiceTest {
    private final PaymentFlowRepository flows = mock(PaymentFlowRepository.class);
    private final PaymentFlowStepRepository steps = mock(PaymentFlowStepRepository.class);
    private final PaymentFlowEvidenceRepository evidence = mock(PaymentFlowEvidenceRepository.class);
    private final WalletRepository wallets = mock(WalletRepository.class);
    private final PaymentBackendClient payment = mock(PaymentBackendClient.class);
    private final SolanaCprClient solana = mock(SolanaCprClient.class);
    private final UnifiedReconciliationService service = new UnifiedReconciliationService(
            flows, steps, evidence, wallets, payment, solana);
    private PaymentFlow flow;

    @BeforeEach
    void setup() {
        flow = new PaymentFlow();
        flow.setId(UUID.randomUUID());
        flow.setJobId(UUID.randomUUID());
        flow.setContractId(UUID.randomUUID());
        flow.setMilestoneId(UUID.randomUUID());
        flow.setClientId(UUID.randomUUID());
        flow.setFreelancerId(UUID.randomUUID());
        flow.setGrossUsd(new BigDecimal("100.00"));
        flow.setEscrowUsdc(new BigDecimal("100.000000"));
        flow.setMint("mock-mint");
        when(wallets.findFirstByUserIdOrderByIdAsc(flow.getClientId())).thenReturn(Optional.of(wallet("client-wallet")));
        when(wallets.findFirstByUserIdOrderByIdAsc(flow.getFreelancerId())).thenReturn(Optional.of(wallet("freelancer-wallet")));
        when(flows.findById(flow.getId())).thenReturn(Optional.of(flow));
    }

    @Test
    void terminalEscrowWithEmptyVaultProvesRecipientCreditForRecordedParticipants() {
        steps(row("USDC_RELEASE", "CONFIRMED"), row("USDC_REFUND", "NOT_STARTED"));
        SolanaEscrowResult chain = escrow("Released", "0", "freelancer-wallet");
        when(solana.findEscrow(flow.getMilestoneId().toString())).thenReturn(Optional.of(chain));

        var boundary = service.inspect(flow).vaultToRecipient();

        assertThat(boundary.status()).isEqualTo("MATCHED");
        assertThat(boundary.code()).isEqualTo("RECIPIENT_BY_ESCROW_INVARIANT");
    }

    @Test
    void terminalEscrowForAnotherParticipantOrWithTokensLeftIsAMismatch() {
        steps(row("USDC_RELEASE", "CONFIRMED"), row("USDC_REFUND", "NOT_STARTED"));
        SolanaEscrowResult chain = escrow("Released", "0", "someone-else");
        when(solana.findEscrow(flow.getMilestoneId().toString())).thenReturn(Optional.of(chain));
        assertThat(service.inspect(flow).vaultToRecipient().status()).isEqualTo("MISMATCH");

        SolanaEscrowResult leftover = escrow("Released", "1", "freelancer-wallet");
        when(solana.findEscrow(flow.getMilestoneId().toString())).thenReturn(Optional.of(leftover));
        assertThat(service.inspect(flow).vaultToRecipient().status()).isEqualTo("MISMATCH");
    }

    @Test
    void nextMoneyStepIsBlockedUntilEveryEarlierBoundaryMatches() {
        steps(row("USD_RECEIVED", "PENDING"), row("CLIENT_USDC", "NOT_STARTED"));

        assertThatThrownBy(() -> service.requireMatched(flow, 1))
                .isInstanceOfSatisfying(ApplicationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_RECONCILIATION_BLOCKED));
        verifyNoInteractions(payment);
    }

    @Test
    void adminReviewIsAuditedButCannotClearABoundaryWithoutMatchingEvidence() {
        steps(row("USD_RECEIVED", "PENDING"));

        assertThatThrownBy(() -> service.review(UUID.randomUUID(), flow.getId(),
                "USD_TO_CLIENT_USDC", "CLEARED_BY_EVIDENCE", "Provider statement checked manually"))
                .isInstanceOf(ApplicationException.class);
        verify(evidence, never()).save(any());

        UUID admin = UUID.randomUUID();
        service.review(admin, flow.getId(), "USD_TO_CLIENT_USDC", "ESCALATED",
                "Statement missing, asked provider for lookup");
        verify(evidence).save(argThat(row -> "ADMIN_RECONCILIATION_REVIEW".equals(row.getKind())
                && "ESCALATED".equals(row.getStatus()) && admin.equals(row.getActorId())
                && "USD_TO_CLIENT_USDC".equals(row.getReference())
                && row.getNote().contains("USD_OR_ONRAMP_PENDING")));
        verifyNoInteractions(payment);
    }

    @Test
    void adminReviewRejectsUnknownBoundaryDecisionOrShortNote() {
        assertThatThrownBy(() -> service.review(UUID.randomUUID(), flow.getId(), "ANY", "ESCALATED", "long enough note"))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> service.review(UUID.randomUUID(), flow.getId(), "USD_TO_CLIENT_USDC", "PAID", "long enough note"))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> service.review(UUID.randomUUID(), flow.getId(), "USD_TO_CLIENT_USDC", "ESCALATED", "short"))
                .isInstanceOf(ApplicationException.class);
        verify(evidence, never()).save(any());
    }

    private void steps(PaymentFlowStep... rows) {
        when(steps.findByPaymentFlowIdOrderByCreatedAtAsc(flow.getId())).thenReturn(List.of(rows));
    }

    private SolanaEscrowResult escrow(String status, String vault, String freelancer) {
        SolanaEscrowResult chain = mock(SolanaEscrowResult.class);
        when(chain.status()).thenReturn(status);
        when(chain.amount()).thenReturn("100000000");
        when(chain.vaultBalanceBaseUnits()).thenReturn(vault);
        when(chain.client()).thenReturn("client-wallet");
        when(chain.freelancer()).thenReturn(freelancer);
        return chain;
    }

    private PaymentFlowStep row(String kind, String status) {
        PaymentFlowStep step = new PaymentFlowStep(); step.setKind(kind); step.setStatus(status); return step;
    }

    private Wallet wallet(String key) {
        Wallet wallet = new Wallet(); wallet.setPublicKey(key); return wallet;
    }
}
