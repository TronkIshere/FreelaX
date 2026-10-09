package com.marketplace.backend.service;

import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.dto.response.payment.UnifiedUsdOrderResult;
import com.marketplace.backend.dto.response.payment.UnifiedUsdStatementResult;
import com.marketplace.backend.dto.response.solana.SolanaConfigResult;
import com.marketplace.backend.entity.PaymentFlow;
import com.marketplace.backend.entity.PaymentFlowStep;
import com.marketplace.backend.entity.Wallet;
import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.entity.ContractStatus;
import com.marketplace.backend.provider.currency.OnRampResult;
import com.marketplace.backend.provider.currency.OnRampQuote;
import com.marketplace.backend.provider.onchain.SolanaOnRampProvider;
import com.marketplace.backend.repository.*;
import com.marketplace.backend.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UnifiedUsdFundingServiceTest {
    private final PaymentFlowRepository flows = mock(PaymentFlowRepository.class);
    private final PaymentFlowStepRepository steps = mock(PaymentFlowStepRepository.class);
    private final PaymentFlowEvidenceRepository evidence = mock(PaymentFlowEvidenceRepository.class);
    private final WorkContractRepository contracts = mock(WorkContractRepository.class);
    private final MilestoneRepository milestones = mock(MilestoneRepository.class);
    private final WalletRepository wallets = mock(WalletRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PaymentBackendClient payment = mock(PaymentBackendClient.class);
    private final SolanaCprClient solana = mock(SolanaCprClient.class);
    private final SolanaCprProperties properties = mock(SolanaCprProperties.class);
    private final SolanaOnRampProvider onramp = mock(SolanaOnRampProvider.class);
    private final PaymentFlowService timelines = mock(PaymentFlowService.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final UnifiedUsdFundingService service = new UnifiedUsdFundingService(flows, steps,
            evidence, contracts, milestones, wallets, users, payment, solana,
            properties, onramp, timelines, transactions);

    @Test
    void mismatchedProviderStatementDoesNotCreateClientUsdc() {
        UUID id = UUID.randomUUID(), milestoneId = UUID.randomUUID();
        PaymentFlow flow = new PaymentFlow();
        flow.setId(id); flow.setMilestoneId(milestoneId); flow.setContractId(UUID.randomUUID());
        flow.setJobId(UUID.randomUUID()); flow.setClientId(UUID.randomUUID());
        flow.setGrossUsd(new BigDecimal("100.00"));
        flow.setEscrowUsdc(new BigDecimal("100.000000"));
        flow.setTermsVersion(1); flow.setMint("mock-mint");
        flow.setQuoteSource("LOCAL_MOCK_USD_USDC_1_TO_1");
        flow.setQuoteExpiresAt(Instant.now().plusSeconds(600));
        flow.setPayerBankCode("VIETCOMBANK");
        flow.setPayerBankAccountNumber("123456789");
        flow.setPayerBankAccountHolderName("CLIENT NAME");
        PaymentFlowStep orderStep = step("USD_ORDER", "PENDING");
        PaymentFlowStep received = step("USD_RECEIVED", "NOT_STARTED");
        PaymentFlowStep usdc = step("CLIENT_USDC", "NOT_STARTED");
        when(flows.findById(id)).thenReturn(Optional.of(flow));
        when(flows.findWithLockByMilestoneId(milestoneId)).thenReturn(Optional.of(flow));
        when(steps.findWithLockByPaymentFlowIdAndKind(id, "USD_ORDER")).thenReturn(Optional.of(orderStep));
        when(steps.findWithLockByPaymentFlowIdAndKind(id, "USD_RECEIVED")).thenReturn(Optional.of(received));
        when(steps.findWithLockByPaymentFlowIdAndKind(id, "CLIENT_USDC")).thenReturn(Optional.of(usdc));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked") Consumer<TransactionStatus> action = inv.getArgument(0);
            action.accept(mock(TransactionStatus.class)); return null;
        }).when(transactions).executeWithoutResult(any());
        SolanaConfigResult config = new SolanaConfigResult(); config.setAcceptedMint("mock-mint");
        when(solana.getConfig()).thenReturn(config);
        when(payment.getUnifiedUsdOrder(id)).thenReturn(new UnifiedUsdOrderResult(id, flow.getJobId(),
                flow.getContractId(), milestoneId, flow.getClientId(), flow.getGrossUsd(),
                flow.getEscrowUsdc(), flow.getPayerBankCode(), flow.getPayerBankAccountNumber(),
                flow.getPayerBankAccountHolderName(), "unified-usd-" + id, "quote-1",
                flow.getQuoteSource(), flow.getQuoteExpiresAt(), "USD_RECEIVED", Instant.now(), true));
        when(payment.getUnifiedUsdStatement(id)).thenReturn(new UnifiedUsdStatementResult(id,
                List.of(new UnifiedUsdStatementResult.Row("usd-received:" + id, id,
                        "USD_RECEIVED", new BigDecimal("99.99"), "USD", "quote-1", Instant.now())),
                Instant.now(), true));

        service.reconcileUsd(id);

        assertThat(received.getStatus()).isEqualTo("UNKNOWN");
        assertThat(usdc.getStatus()).isEqualTo("NOT_STARTED");
        verifyNoInteractions(onramp);
        verify(evidence, never()).save(any());
    }

    @Test
    void rejectedBeforeSubmissionCanRecoverUsingSamePurchaseId() {
        UUID id = UUID.randomUUID(), clientId = UUID.randomUUID();
        PaymentFlow flow = new PaymentFlow();
        flow.setId(id); flow.setClientId(clientId);
        flow.setGrossUsd(new BigDecimal("15.00"));
        flow.setEscrowUsdc(new BigDecimal("15.000000"));
        PaymentFlowStep received = step("USD_RECEIVED", "CONFIRMED");
        PaymentFlowStep usdc = step("CLIENT_USDC", "FAILED");
        Wallet wallet = new Wallet(); wallet.setPublicKey("client-wallet");
        when(flows.findById(id)).thenReturn(Optional.of(flow));
        when(wallets.findFirstByUserIdOrderByIdAsc(clientId)).thenReturn(Optional.of(wallet));
        when(steps.findWithLockByPaymentFlowIdAndKind(id, "USD_RECEIVED"))
                .thenReturn(Optional.of(received));
        when(steps.findWithLockByPaymentFlowIdAndKind(id, "CLIENT_USDC"))
                .thenReturn(Optional.of(usdc));
        doAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0))
                .doInTransaction(mock(TransactionStatus.class))).when(transactions).execute(any());
        doAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
        when(onramp.execute(any(OnRampQuote.class), eq("client-wallet")))
                .thenReturn(OnRampResult.failed(null, null, null, "local authority mismatch"))
                .thenReturn(OnRampResult.confirmed("chain-tx", "client-ata", "receipt-pda",
                        new BigDecimal("15.000000")));

        service.advanceOnramp(id);
        assertThat(usdc.getStatus()).isEqualTo("UNKNOWN");
        assertThat(usdc.getTransactionSignature()).isNull();
        usdc.setRetryAfter(Instant.now().minusSeconds(1));
        service.advanceOnramp(id);
        assertThat(usdc.getStatus()).isEqualTo("CONFIRMED");
        assertThat(usdc.getIdempotencyKey()).isEqualTo("unified-onramp-" + id);
        verify(onramp, times(2)).execute(argThat(quote ->
                quote.purchaseId().equals(Long.toUnsignedString(id.getMostSignificantBits()))),
                eq("client-wallet"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void droppedOnrampIsResentOnlyWhileNoReceiptExists(boolean receiptExists) {
        UUID id = UUID.randomUUID(), clientId = UUID.randomUUID();
        PaymentFlow flow = new PaymentFlow();
        flow.setId(id); flow.setClientId(clientId);
        flow.setGrossUsd(new BigDecimal("5.00")); flow.setEscrowUsdc(new BigDecimal("5.000000"));
        PaymentFlowStep received = step("USD_RECEIVED", "CONFIRMED");
        PaymentFlowStep usdc = step("CLIENT_USDC", "FAILED");
        usdc.setTransactionSignature("dropped-tx");
        usdc.setAccountAddress("receipt-pda");
        Wallet wallet = new Wallet(); wallet.setPublicKey("client-wallet");
        when(flows.findById(id)).thenReturn(Optional.of(flow));
        when(wallets.findFirstByUserIdOrderByIdAsc(clientId)).thenReturn(Optional.of(wallet));
        when(solana.findMockOnrampReceipt(eq("client-wallet"), any())).thenReturn(receiptExists
                ? Optional.of(new com.marketplace.backend.dto.response.solana.MockOnrampReceiptResult()) : Optional.empty());
        when(steps.findWithLockByPaymentFlowIdAndKind(id, "USD_RECEIVED")).thenReturn(Optional.of(received));
        when(steps.findWithLockByPaymentFlowIdAndKind(id, "CLIENT_USDC")).thenReturn(Optional.of(usdc));
        doAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0))
                .doInTransaction(mock(TransactionStatus.class))).when(transactions).execute(any());
        doAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
        when(onramp.execute(any(OnRampQuote.class), eq("client-wallet")))
                .thenReturn(OnRampResult.confirmed("new-tx", "client-ata", "receipt-pda", new BigDecimal("5.000000")));

        service.advanceOnramp(id);

        if (receiptExists) {
            verify(onramp, never()).execute(any(), any());
            assertThat(usdc.getStatus()).isEqualTo("FAILED");
        } else {
            verify(onramp).execute(any(OnRampQuote.class), eq("client-wallet"));
            verify(onramp, never()).resume(any(), any(), any(), any(), any(), any());
            assertThat(usdc.getStatus()).isEqualTo("CONFIRMED");
            assertThat(usdc.getTransactionSignature()).isEqualTo("new-tx");
        }
    }

    @Test
    void repeatedProviderStatementConfirmsUsdExactlyOnce() {
        UUID id = UUID.randomUUID(), milestoneId = UUID.randomUUID();
        PaymentFlow flow = lockedFlow(id, milestoneId);
        PaymentFlowStep orderStep = step("USD_ORDER", "PENDING");
        PaymentFlowStep received = step("USD_RECEIVED", "NOT_STARTED");
        PaymentFlowStep usdc = step("CLIENT_USDC", "NOT_STARTED");
        stubOrder(flow, orderStep, received, usdc, "mock-mint", new BigDecimal("100.00"));

        service.reconcileUsd(id);
        service.reconcileUsd(id);

        assertThat(received.getStatus()).isEqualTo("CONFIRMED");
        verify(evidence, times(1)).save(argThat(e -> "USD_RECEIVED".equals(e.getKind())));
    }

    @Test
    void rotatedMintAfterTermsAcceptanceIsNotSilentlyLockedIntoTheOrder() {
        UUID id = UUID.randomUUID(), milestoneId = UUID.randomUUID();
        PaymentFlow flow = lockedFlow(id, milestoneId);
        flow.setTermsVersion(0); flow.setMint("accepted-mint"); flow.setNetwork("localnet");
        when(properties.getNetwork()).thenReturn("localnet");
        PaymentFlowStep orderStep = step("USD_ORDER", "PROCESSING");
        PaymentFlowStep received = step("USD_RECEIVED", "NOT_STARTED");
        PaymentFlowStep usdc = step("CLIENT_USDC", "NOT_STARTED");
        stubOrder(flow, orderStep, received, usdc, "rotated-mint", new BigDecimal("100.00"));

        service.reconcileUsd(id);

        assertThat(flow.getTermsVersion()).isZero();
        assertThat(flow.getMint()).isEqualTo("accepted-mint");
        assertThat(orderStep.getStatus()).isEqualTo("UNKNOWN");
        assertThat(received.getStatus()).isEqualTo("NOT_STARTED");
        verify(evidence, never()).save(any());
    }

    @Test
    void usdOrderCannotBeSubmittedAfterQuoteOrFundingDeadline() {
        UUID id = UUID.randomUUID(), milestoneId = UUID.randomUUID();
        PaymentFlow flow = lockedFlow(id, milestoneId);
        WorkContract contract = new WorkContract();
        contract.setId(flow.getContractId()); contract.setClientUserId(flow.getClientId());
        contract.setPaymentRail(PaymentFlow.RAIL); contract.setStatus(ContractStatus.PENDING_FUNDING);
        when(contracts.findById(flow.getContractId())).thenReturn(Optional.of(contract));
        when(flows.findByMilestoneId(milestoneId)).thenReturn(Optional.of(flow));

        flow.setFundingExpiresAt(Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> service.submitUsdOrder(flow.getClientId(), flow.getContractId(), milestoneId))
                .isInstanceOf(ApplicationException.class);
        flow.setFundingExpiresAt(Instant.now().plusSeconds(3600));
        flow.setQuoteExpiresAt(Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> service.submitUsdOrder(flow.getClientId(), flow.getContractId(), milestoneId))
                .isInstanceOf(ApplicationException.class);
        verifyNoInteractions(payment);
    }

    @Test
    void usdOrderIsRefusedUntilTheClientWalletIsLinked() {
        UUID id = UUID.randomUUID(), milestoneId = UUID.randomUUID();
        PaymentFlow flow = lockedFlow(id, milestoneId);
        flow.setTermsVersion(0);
        com.marketplace.backend.entity.Milestone milestone = new com.marketplace.backend.entity.Milestone();
        milestone.setId(milestoneId); milestone.setContractId(flow.getContractId());
        milestone.setStatus(com.marketplace.backend.entity.MilestoneStatus.PENDING_FUNDING);
        milestone.setCurrency("USD"); milestone.setAmount(flow.getGrossUsd());
        WorkContract contract = new WorkContract();
        contract.setId(flow.getContractId()); contract.setClientUserId(flow.getClientId());
        contract.setPaymentRail(PaymentFlow.RAIL); contract.setStatus(ContractStatus.PENDING_FUNDING);
        when(milestones.findWithLockById(milestoneId)).thenReturn(Optional.of(milestone));
        when(contracts.findById(flow.getContractId())).thenReturn(Optional.of(contract));
        when(flows.findWithLockByMilestoneId(milestoneId)).thenReturn(Optional.of(flow));
        when(wallets.findFirstByUserIdOrderByIdAsc(flow.getClientId())).thenReturn(Optional.empty());
        doAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0))
                .doInTransaction(mock(TransactionStatus.class))).when(transactions).execute(any());

        assertThatThrownBy(() -> service.openUsdOrder(flow.getClientId(), flow.getContractId(), milestoneId, "key-1"))
                .isInstanceOfSatisfying(ApplicationException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(com.marketplace.backend.exception.ErrorCode.FUNDING_PAYMENT_METHOD_INVALID));
        verifyNoInteractions(payment);
    }

    private PaymentFlow lockedFlow(UUID id, UUID milestoneId) {
        PaymentFlow flow = new PaymentFlow();
        flow.setId(id); flow.setMilestoneId(milestoneId); flow.setContractId(UUID.randomUUID());
        flow.setJobId(UUID.randomUUID()); flow.setClientId(UUID.randomUUID());
        flow.setGrossUsd(new BigDecimal("100.00"));
        flow.setEscrowUsdc(new BigDecimal("100.000000"));
        flow.setTermsVersion(1); flow.setMint("mock-mint");
        flow.setQuoteSource("LOCAL_MOCK_USD_USDC_1_TO_1");
        flow.setQuoteExpiresAt(Instant.now().plusSeconds(600));
        flow.setFundingExpiresAt(Instant.now().plusSeconds(3600));
        flow.setPayerBankCode("VIETCOMBANK");
        flow.setPayerBankAccountNumber("123456789");
        flow.setPayerBankAccountHolderName("CLIENT NAME");
        return flow;
    }

    private void stubOrder(PaymentFlow flow, PaymentFlowStep orderStep, PaymentFlowStep received,
            PaymentFlowStep usdc, String configMint, BigDecimal statementAmount) {
        UUID id = flow.getId();
        when(flows.findById(id)).thenReturn(Optional.of(flow));
        when(flows.findWithLockByMilestoneId(flow.getMilestoneId())).thenReturn(Optional.of(flow));
        when(steps.findWithLockByPaymentFlowIdAndKind(id, "USD_ORDER")).thenReturn(Optional.of(orderStep));
        when(steps.findWithLockByPaymentFlowIdAndKind(id, "USD_RECEIVED")).thenReturn(Optional.of(received));
        when(steps.findWithLockByPaymentFlowIdAndKind(id, "CLIENT_USDC")).thenReturn(Optional.of(usdc));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked") Consumer<TransactionStatus> action = inv.getArgument(0);
            action.accept(mock(TransactionStatus.class)); return null;
        }).when(transactions).executeWithoutResult(any());
        SolanaConfigResult config = new SolanaConfigResult(); config.setAcceptedMint(configMint);
        when(solana.getConfig()).thenReturn(config);
        when(payment.getUnifiedUsdOrder(id)).thenReturn(new UnifiedUsdOrderResult(id, flow.getJobId(),
                flow.getContractId(), flow.getMilestoneId(), flow.getClientId(), flow.getGrossUsd(),
                flow.getEscrowUsdc(), flow.getPayerBankCode(), flow.getPayerBankAccountNumber(),
                flow.getPayerBankAccountHolderName(), "unified-usd-" + id, "quote-1",
                flow.getQuoteSource(), flow.getQuoteExpiresAt(), "USD_RECEIVED", Instant.now(), true));
        when(payment.getUnifiedUsdStatement(id)).thenReturn(new UnifiedUsdStatementResult(id,
                List.of(new UnifiedUsdStatementResult.Row("usd-received:" + id, id,
                        "USD_RECEIVED", statementAmount, "USD", "quote-1", Instant.now())),
                Instant.now(), true));
    }

    private PaymentFlowStep step(String kind, String status) {
        PaymentFlowStep row = new PaymentFlowStep(); row.setKind(kind); row.setStatus(status); return row;
    }
}
