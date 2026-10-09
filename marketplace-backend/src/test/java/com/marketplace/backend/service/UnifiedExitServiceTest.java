package com.marketplace.backend.service;

import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.dto.response.payment.UnifiedFiatExitResult;
import com.marketplace.backend.dto.response.payment.UnifiedFiatExitStatementResult;
import com.marketplace.backend.dto.response.solana.SolanaEscrowResult;
import com.marketplace.backend.dto.response.solana.SolanaConfigResult;
import com.marketplace.backend.dto.response.solana.SolanaWithdrawalResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UnifiedExitServiceTest {
    private final PaymentFlowRepository flows = mock(PaymentFlowRepository.class);
    private final PaymentFlowStepRepository steps = mock(PaymentFlowStepRepository.class);
    private final PaymentFlowEvidenceRepository evidence = mock(PaymentFlowEvidenceRepository.class);
    private final WorkContractRepository contracts = mock(WorkContractRepository.class);
    private final MilestoneRepository milestones = mock(MilestoneRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final WalletRepository wallets = mock(WalletRepository.class);
    private final EscrowContractRepository escrows = mock(EscrowContractRepository.class);
    private final SolanaCprClient solana = mock(SolanaCprClient.class);
    private final SolanaCprProperties properties = mock(SolanaCprProperties.class);
    private final PaymentBackendClient payment = mock(PaymentBackendClient.class);
    private final PaymentFlowService timelines = mock(PaymentFlowService.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final UnifiedReconciliationService reconciliation = mock(UnifiedReconciliationService.class);
    private final UnifiedExitService service = new UnifiedExitService(flows, steps, evidence,
            contracts, milestones, users, wallets, escrows, solana, properties,
            payment, timelines, transactions, reconciliation);

    @Test
    void cannotPrepareWithdrawalBeforeChainReleaseOrRefund() {
        UUID actor = UUID.randomUUID(), contractId = UUID.randomUUID(), milestoneId = UUID.randomUUID();
        PaymentFlow flow = new PaymentFlow();
        flow.setId(UUID.randomUUID()); flow.setContractId(contractId);
        WorkContract contract = new WorkContract();
        contract.setPaymentRail(PaymentFlow.RAIL); contract.setClientUserId(actor);
        when(contracts.findById(contractId)).thenReturn(Optional.of(contract));
        when(flows.findByMilestoneId(milestoneId)).thenReturn(Optional.of(flow));
        when(steps.findByPaymentFlowIdOrderByCreatedAtAsc(flow.getId())).thenReturn(List.of(
                row("USDC_RELEASE", "PENDING"), row("USDC_REFUND", "NOT_STARTED")));

        assertThatThrownBy(() -> service.prepare(actor, contractId, milestoneId))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(payment, solana, transactions);
    }

    @Test
    void vndAndFeeRequireMatchingIndependentStatementsAfterWithdrawal() {
        UUID flowId = UUID.randomUUID(), contractId = UUID.randomUUID(), milestoneId = UUID.randomUUID();
        PaymentFlow flow = new PaymentFlow();
        flow.setId(flowId); flow.setContractId(contractId); flow.setMilestoneId(milestoneId);
        flow.setFreelancerId(UUID.randomUUID());
        flow.setJobId(UUID.randomUUID()); flow.setGrossUsd(new BigDecimal("100.00"));
        flow.setEscrowUsdc(new BigDecimal("100.000000")); flow.setMint("mock-mint");
        WorkContract contract = new WorkContract();
        contract.setStatus(ContractStatus.COMPLETED);
        Milestone milestone = new Milestone();
        milestone.setStatus(MilestoneStatus.RELEASED);
        EscrowContract escrow = new EscrowContract(); escrow.setMint("mock-mint");
        escrow.setEscrowAddress("escrow-pda");
        SolanaEscrowResult chain = mock(SolanaEscrowResult.class);
        when(chain.address()).thenReturn("escrow-pda");
        when(chain.mint()).thenReturn("mock-mint");
        when(chain.amount()).thenReturn("100000000");
        when(chain.status()).thenReturn("Released");
        when(chain.vaultBalanceBaseUnits()).thenReturn("0");
        when(solana.findEscrow(milestoneId.toString())).thenReturn(Optional.of(chain));
        PaymentFlowStep release = row("USDC_RELEASE", "CONFIRMED");
        PaymentFlowStep refund = row("USDC_REFUND", "NOT_STARTED");
        PaymentFlowStep withdrawal = row("WITHDRAWAL", "CONFIRMED");
        withdrawal.setReference("withdrawal-pda");
        withdrawal.setAccountAddress("rate-pda");
        withdrawal.setTokenAccount("withdrawal-pda");
        withdrawal.setBeneficiary("bank:account:holder");
        withdrawal.setIdempotencyKey("unified-exit-" + flowId);
        withdrawal.setFeeUsdc(new BigDecimal("3.000000"));
        withdrawal.setVndRate(new BigDecimal("25000.00"));
        withdrawal.setPayoutVnd(new BigDecimal("2425000"));
        PaymentFlowStep payout = row("VND_PAYOUT", "PENDING");
        PaymentFlowStep fee = row("PLATFORM_FEE", "PENDING");
        Map<String, PaymentFlowStep> map = Map.of("USDC_RELEASE", release, "USDC_REFUND", refund,
                "WITHDRAWAL", withdrawal, "VND_PAYOUT", payout, "PLATFORM_FEE", fee);
        when(flows.findById(flowId)).thenReturn(Optional.of(flow));
        when(contracts.findById(contractId)).thenReturn(Optional.of(contract));
        when(milestones.findById(milestoneId)).thenReturn(Optional.of(milestone));
        when(escrows.findByContractId(contractId)).thenReturn(Optional.of(escrow));
        Wallet wallet = new Wallet(); wallet.setPublicKey("freelancer-wallet");
        when(wallets.findFirstByUserIdOrderByIdAsc(flow.getFreelancerId())).thenReturn(Optional.of(wallet));
        SolanaWithdrawalResult chainWithdrawal = new SolanaWithdrawalResult();
        chainWithdrawal.setAddress("withdrawal-pda");
        chainWithdrawal.setFreelancer("freelancer-wallet");
        chainWithdrawal.setWithdrawalId(Long.toUnsignedString(flowId.getMostSignificantBits()));
        chainWithdrawal.setTokenAmount("100000000");
        chainWithdrawal.setMint("mock-mint");
        chainWithdrawal.setRateSnapshot("rate-pda");
        chainWithdrawal.setStatus("Pending");
        chainWithdrawal.setTreasury("treasury-ata");
        chainWithdrawal.setFiatAmountVnd("2500000");
        when(solana.findWithdrawal("freelancer-wallet", chainWithdrawal.getWithdrawalId()))
                .thenReturn(Optional.of(chainWithdrawal));
        SolanaConfigResult config = new SolanaConfigResult(); config.setAcceptedMint("mock-mint");
        when(solana.getConfig()).thenReturn(config);
        when(steps.findByPaymentFlowIdOrderByCreatedAtAsc(flowId)).thenAnswer(inv -> List.copyOf(map.values()));
        for (String kind : map.keySet())
            when(steps.findWithLockByPaymentFlowIdAndKind(flowId, kind)).thenReturn(Optional.of(map.get(kind)));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked") Consumer<TransactionStatus> action = inv.getArgument(0);
            action.accept(mock(TransactionStatus.class)); return null;
        }).when(transactions).executeWithoutResult(any());
        UnifiedFiatExitResult exit = new UnifiedFiatExitResult(flowId, flow.getJobId(), contractId,
                milestoneId, "PAYOUT", withdrawal.getIdempotencyKey(), withdrawal.getReference(),
                withdrawal.getBeneficiary(), flow.getEscrowUsdc(), withdrawal.getFeeUsdc(),
                flow.getGrossUsd(), withdrawal.getVndRate(), withdrawal.getPayoutVnd(),
                "CONFIRMED", Instant.now(), true);
        when(payment.getUnifiedFiatExit(flowId)).thenReturn(exit);
        var payoutRow = new UnifiedFiatExitStatementResult.Entry("unified-exit:" + flowId + ":VND_PAYOUT",
                flowId, "VND_PAYOUT", new BigDecimal("2425000"), "VND", "withdrawal-pda", Instant.now());
        var feeRow = new UnifiedFiatExitStatementResult.Entry("unified-exit:" + flowId + ":PLATFORM_FEE",
                flowId, "PLATFORM_FEE", new BigDecimal("3.000000"), "USDC", "withdrawal-pda", Instant.now());
        when(payment.getUnifiedFiatExitStatement(flowId)).thenReturn(new UnifiedFiatExitStatementResult(
                flowId, List.of(payoutRow), Instant.now(), true));
        service.reconcileFiat(flowId);
        assertThat(payout.getStatus()).isEqualTo("UNKNOWN");
        assertThat(fee.getStatus()).isEqualTo("PENDING");
        verify(evidence, never()).save(any());

        when(payment.getUnifiedFiatExitStatement(flowId)).thenReturn(new UnifiedFiatExitStatementResult(
                flowId, List.of(payoutRow, feeRow), Instant.now(), true));
        service.reconcileFiat(flowId);
        assertThat(payout.getStatus()).isEqualTo("CONFIRMED");
        assertThat(fee.getStatus()).isEqualTo("CONFIRMED");
        verify(evidence, times(2)).save(any());
    }

    /** Released flow with a confirmed WithdrawalRecord, ready for the fiat exit. */
    private record Payout(UUID flowId, UUID contractId, UUID milestoneId, PaymentFlow flow,
            PaymentFlowStep withdrawal, PaymentFlowStep payout, PaymentFlowStep fee) { }

    private Payout payoutFixture(String withdrawalStatus) {
        UUID flowId = UUID.randomUUID(), contractId = UUID.randomUUID(), milestoneId = UUID.randomUUID();
        PaymentFlow flow = new PaymentFlow();
        flow.setId(flowId); flow.setContractId(contractId); flow.setMilestoneId(milestoneId);
        flow.setFreelancerId(UUID.randomUUID()); flow.setClientId(UUID.randomUUID());
        flow.setJobId(UUID.randomUUID()); flow.setGrossUsd(new BigDecimal("100.00"));
        flow.setEscrowUsdc(new BigDecimal("100.000000")); flow.setMint("mock-mint");
        WorkContract contract = new WorkContract();
        contract.setStatus(ContractStatus.COMPLETED); contract.setPaymentRail(PaymentFlow.RAIL);
        contract.setFreelancerId(flow.getFreelancerId()); contract.setClientUserId(flow.getClientId());
        Milestone milestone = new Milestone(); milestone.setStatus(MilestoneStatus.RELEASED);
        EscrowContract escrow = new EscrowContract(); escrow.setMint("mock-mint");
        escrow.setEscrowAddress("escrow-pda");
        SolanaEscrowResult chain = mock(SolanaEscrowResult.class);
        when(chain.address()).thenReturn("escrow-pda");
        when(chain.mint()).thenReturn("mock-mint");
        when(chain.amount()).thenReturn("100000000");
        when(chain.status()).thenReturn("Released");
        when(chain.vaultBalanceBaseUnits()).thenReturn("0");
        when(solana.findEscrow(milestoneId.toString())).thenReturn(Optional.of(chain));
        PaymentFlowStep withdrawal = row("WITHDRAWAL", withdrawalStatus);
        withdrawal.setReference("withdrawal-pda");
        withdrawal.setAccountAddress("rate-pda");
        withdrawal.setTokenAccount("withdrawal-pda");
        withdrawal.setBeneficiary("bank:account:holder");
        withdrawal.setIdempotencyKey("unified-exit-" + flowId);
        withdrawal.setFeeUsdc(new BigDecimal("3.000000"));
        withdrawal.setVndRate(new BigDecimal("25000.00"));
        withdrawal.setPayoutVnd(new BigDecimal("2425000"));
        PaymentFlowStep payout = row("VND_PAYOUT", "PENDING");
        PaymentFlowStep fee = row("PLATFORM_FEE", "PENDING");
        Map<String, PaymentFlowStep> map = Map.of("USDC_RELEASE", row("USDC_RELEASE", "CONFIRMED"),
                "USDC_REFUND", row("USDC_REFUND", "NOT_STARTED"), "WITHDRAWAL", withdrawal,
                "VND_PAYOUT", payout, "PLATFORM_FEE", fee);
        when(flows.findById(flowId)).thenReturn(Optional.of(flow));
        when(flows.findByMilestoneId(milestoneId)).thenReturn(Optional.of(flow));
        when(contracts.findById(contractId)).thenReturn(Optional.of(contract));
        when(milestones.findById(milestoneId)).thenReturn(Optional.of(milestone));
        when(escrows.findByContractId(contractId)).thenReturn(Optional.of(escrow));
        Wallet wallet = new Wallet(); wallet.setPublicKey("freelancer-wallet");
        when(wallets.findFirstByUserIdOrderByIdAsc(flow.getFreelancerId())).thenReturn(Optional.of(wallet));
        SolanaWithdrawalResult record = new SolanaWithdrawalResult();
        record.setAddress("withdrawal-pda");
        record.setFreelancer("freelancer-wallet");
        record.setWithdrawalId(Long.toUnsignedString(flowId.getMostSignificantBits()));
        record.setTokenAmount("100000000");
        record.setMint("mock-mint");
        record.setRateSnapshot("rate-pda");
        record.setStatus("Pending");
        record.setTreasury("treasury-ata");
        record.setFiatAmountVnd("2500000");
        when(solana.findWithdrawal("freelancer-wallet", record.getWithdrawalId())).thenReturn(Optional.of(record));
        SolanaConfigResult config = new SolanaConfigResult(); config.setAcceptedMint("mock-mint");
        when(solana.getConfig()).thenReturn(config);
        when(steps.findByPaymentFlowIdOrderByCreatedAtAsc(flowId)).thenAnswer(inv -> List.copyOf(map.values()));
        for (String kind : map.keySet())
            when(steps.findWithLockByPaymentFlowIdAndKind(flowId, kind)).thenReturn(Optional.of(map.get(kind)));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked") Consumer<TransactionStatus> action = inv.getArgument(0);
            action.accept(mock(TransactionStatus.class)); return null;
        }).when(transactions).executeWithoutResult(any());
        return new Payout(flowId, contractId, milestoneId, flow, withdrawal, payout, fee);
    }

    private UnifiedFiatExitResult exit(Payout x, String status) {
        return new UnifiedFiatExitResult(x.flowId(), x.flow().getJobId(), x.contractId(), x.milestoneId(),
                "PAYOUT", x.withdrawal().getIdempotencyKey(), x.withdrawal().getReference(),
                x.withdrawal().getBeneficiary(), x.flow().getEscrowUsdc(), x.withdrawal().getFeeUsdc(),
                x.flow().getGrossUsd(), x.withdrawal().getVndRate(), x.withdrawal().getPayoutVnd(),
                status, Instant.now(), true);
    }

    private UnifiedFiatExitStatementResult paidStatement(UUID flowId) {
        return new UnifiedFiatExitStatementResult(flowId, List.of(
                new UnifiedFiatExitStatementResult.Entry("unified-exit:" + flowId + ":VND_PAYOUT", flowId,
                        "VND_PAYOUT", new BigDecimal("2425000"), "VND", "withdrawal-pda", Instant.now()),
                new UnifiedFiatExitStatementResult.Entry("unified-exit:" + flowId + ":PLATFORM_FEE", flowId,
                        "PLATFORM_FEE", new BigDecimal("3.000000"), "USDC", "withdrawal-pda", Instant.now())),
                Instant.now(), true);
    }

    private static HttpClientErrorException notFound() {
        return HttpClientErrorException.create(org.springframework.http.HttpStatus.NOT_FOUND, "Not Found",
                org.springframework.http.HttpHeaders.EMPTY, new byte[0], null);
    }

    @Test
    void responseLostAfterProviderPaidIsRecoveredByLookupWithoutASecondPayout() {
        Payout x = payoutFixture("CONFIRMED");
        when(payment.getUnifiedFiatExit(x.flowId())).thenThrow(notFound())
                .thenReturn(exit(x, "CONFIRMED"));
        when(payment.requestUnifiedFiatExit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("read timed out"));
        when(payment.getUnifiedFiatExitStatement(x.flowId())).thenReturn(paidStatement(x.flowId()));

        assertThatThrownBy(() -> service.reconcileFiat(x.flowId())).isInstanceOf(RuntimeException.class);
        assertThat(x.payout().getStatus()).isEqualTo("PENDING");
        service.reconcileFiat(x.flowId());

        assertThat(x.payout().getStatus()).isEqualTo("CONFIRMED");
        assertThat(x.fee().getStatus()).isEqualTo("CONFIRMED");
        verify(payment, times(1)).requestUnifiedFiatExit(eq(x.flowId()), any(), any(), any(), eq("PAYOUT"),
                eq("unified-exit-" + x.flowId()), eq("withdrawal-pda"), eq("bank:account:holder"), any(), any());
    }

    @Test
    void reconciliationMismatchBlocksTheFiatInstruction() {
        Payout x = payoutFixture("CONFIRMED");
        when(payment.getUnifiedFiatExit(x.flowId())).thenThrow(notFound());
        doThrow(new com.marketplace.backend.exception.ApplicationException(
                com.marketplace.backend.exception.ErrorCode.PAYMENT_RECONCILIATION_BLOCKED))
                .when(reconciliation).requireMatched(x.flow(), 3);

        assertThatThrownBy(() -> service.reconcileFiat(x.flowId())).isInstanceOf(RuntimeException.class);
        verify(payment, never()).requestUnifiedFiatExit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(x.payout().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void restartAfterChainSuccessReusesTheWithdrawalRecordInsteadOfResubmitting() {
        Payout x = payoutFixture("PROCESSING");
        x.withdrawal().setBuildSessionId("build-1");
        x.withdrawal().setQuoteExpiresAt(Instant.now().plusSeconds(300));
        x.withdrawal().setRateId("rate-1");
        x.withdrawal().setReference(null);
        when(payment.getUnifiedFiatExit(x.flowId())).thenReturn(exit(x, "PENDING"));

        service.submit(x.flow().getFreelancerId(), x.contractId(), x.milestoneId(), "build-1", "signed-tx");

        verify(solana, never()).submitEscrowSigned(any(), any());
        assertThat(x.withdrawal().getStatus()).isEqualTo("CONFIRMED");
        assertThat(x.withdrawal().getReference()).isEqualTo("withdrawal-pda");
    }

    @Test
    void abandonedUnsignedQuoteReturnsToPendingAndStopsPolling() {
        UUID flowId = UUID.randomUUID();
        PaymentFlowStep withdrawal = row("WITHDRAWAL", "PROCESSING");
        withdrawal.setPaymentFlowId(flowId);
        withdrawal.setRateId("rate-1");
        withdrawal.setQuoteExpiresAt(Instant.now().minusSeconds(600));
        when(steps.findTop50ByKindAndStatusInOrderByUpdatedAtAsc(eq("WITHDRAWAL"), any())).thenReturn(List.of(withdrawal));
        when(steps.findWithLockByPaymentFlowIdAndKind(flowId, "WITHDRAWAL")).thenReturn(Optional.of(withdrawal));
        when(flows.findById(flowId)).thenReturn(Optional.empty());
        doAnswer(inv -> {
            @SuppressWarnings("unchecked") Consumer<TransactionStatus> action = inv.getArgument(0);
            action.accept(mock(TransactionStatus.class)); return null;
        }).when(transactions).executeWithoutResult(any());

        service.reconcile();
        assertThat(withdrawal.getStatus()).isEqualTo("PENDING");
        service.reconcile();
        verify(flows, times(1)).findById(flowId);

        withdrawal.setStatus("PROCESSING");
        withdrawal.setTransactionSignature("signed-and-sent");
        service.reconcile();
        assertThat(withdrawal.getStatus()).isEqualTo("PROCESSING");
    }

    private PaymentFlowStep row(String kind, String status) {
        PaymentFlowStep step = new PaymentFlowStep(); step.setKind(kind); step.setStatus(status); return step;
    }
}
