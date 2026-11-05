package com.marketplace.backend.service;

import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Rail isolation: the Solana escrow funding path must refuse a contract that chose the Avalanche rail. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SolanaEscrowFundingServiceTest {
    @Mock WorkContractRepository contracts;
    @Mock MilestoneRepository milestones;
    @Mock JobRepository jobs;
    @Mock WalletRepository wallets;
    @Mock FundingTransactionRepository simulatedFunding;
    @Mock EscrowContractRepository escrows;
    @Mock SolanaCprClient solana;
    @Mock NotificationService notifications;
    @Mock PaymentFlowRepository paymentFlows;
    @Mock UnifiedUsdFundingService unifiedFunding;
    @Mock PaymentFlowService paymentFlowService;
    @Mock UnifiedReconciliationService reconciliation;
    @Mock LocalAutoWalletService autoWallets;
    @InjectMocks SolanaEscrowFundingService service;

    WorkContract contract;
    Milestone milestone;
    Job job;

    @BeforeEach
    void setUp() {
        contract = new WorkContract();
        contract.setId(UUID.randomUUID());
        contract.setJobId(UUID.randomUUID());
        contract.setClientUserId(UUID.randomUUID());
        contract.setFreelancerId(UUID.randomUUID());
        contract.setBudgetUsd(new BigDecimal("500.00"));
        contract.setStatus(ContractStatus.PENDING_FUNDING);
        contract.setCreatedAt(java.time.LocalDateTime.now());
        milestone = new Milestone();
        milestone.setId(UUID.randomUUID());
        milestone.setContractId(contract.getId());
        milestone.setAmount(new BigDecimal("500.00"));
        milestone.setCurrency("USD");
        milestone.setStatus(MilestoneStatus.PENDING_FUNDING);
        job = new Job();
        job.setId(contract.getJobId());
        job.setStatus(JobStatus.AWAITING_PAYMENT);
        when(contracts.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(milestones.findWithLockById(milestone.getId())).thenReturn(Optional.of(milestone));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(paymentFlowService.fundingDeadline(any())).thenReturn(Instant.now().plusSeconds(3600));
        when(simulatedFunding.existsByMilestoneIdAndStatusIn(any(), any())).thenReturn(false);
        when(escrows.findByContractId(contract.getId())).thenReturn(Optional.empty());
        Wallet clientWallet = new Wallet();
        clientWallet.setPublicKey("ClientWalletPublicKey");
        when(wallets.findFirstByUserIdOrderByIdAsc(contract.getClientUserId())).thenReturn(Optional.of(clientWallet));
    }

    @Test
    void anAvalancheContractIsRefusedBeforeAnyWalletOrChainAccessAndNothingChanges() {
        contract.setPaymentNetworkId("avalanche-local");
        assertThatThrownBy(() -> service.prepare(contract.getClientUserId(), contract.getId(), milestone.getId(), "ClientWalletPublicKey"))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode()).isEqualTo(ErrorCode.FUNDING_INVALID_STATE);
        verifyNoInteractions(solana);
        verify(escrows, never()).saveAndFlush(any());
        assertThat(contract.getPaymentRail()).isNull();
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.PENDING_FUNDING);
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.PENDING_FUNDING);
    }

    @Test
    void aLegacyContractIsNotStoppedByTheAvalancheGuard() {
        for (String networkId : new String[]{null, "", "solana-devnet"}) {
            contract.setPaymentNetworkId(networkId);
            // it passes the guard and the state checks, and stops at the next check: the connected wallet differs
            assertThatThrownBy(() -> service.prepare(contract.getClientUserId(), contract.getId(), milestone.getId(), "SomeOtherWallet"))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ex -> ((ApplicationException) ex).getErrorCode()).isEqualTo(ErrorCode.FUNDING_PAYMENT_METHOD_INVALID);
        }
        verify(escrows, never()).saveAndFlush(any());
    }
}
