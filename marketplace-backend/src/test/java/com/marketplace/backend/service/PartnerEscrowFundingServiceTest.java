package com.marketplace.backend.service;

import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Rail isolation: the partner-escrow funding path must refuse a contract that chose the Avalanche rail. */
class PartnerEscrowFundingServiceTest {
    FundingTransactionRepository transactions;
    WorkContractRepository contracts;
    MilestoneRepository milestones;
    JobRepository jobs;
    UserRepository users;
    PaymentBackendClient payment;
    PartnerEscrowFundingService service;
    WorkContract contract;
    Milestone milestone;
    Job job;

    @BeforeEach
    void setUp() {
        transactions = mock(FundingTransactionRepository.class);
        contracts = mock(WorkContractRepository.class);
        milestones = mock(MilestoneRepository.class);
        jobs = mock(JobRepository.class);
        users = mock(UserRepository.class);
        payment = mock(PaymentBackendClient.class);
        TransactionTemplate tx = new TransactionTemplate(new PlatformTransactionManager() {
            @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return mock(TransactionStatus.class); }
            @Override public void commit(TransactionStatus status) { }
            @Override public void rollback(TransactionStatus status) { }
        });
        service = new PartnerEscrowFundingService(transactions, contracts, milestones, jobs, users, mock(EscrowContractRepository.class),
                payment, mock(PartnerReconciliationService.class), mock(NotificationService.class), tx);
        contract = new WorkContract();
        contract.setId(UUID.randomUUID());
        contract.setJobId(UUID.randomUUID());
        contract.setClientUserId(UUID.randomUUID());
        contract.setFreelancerId(UUID.randomUUID());
        contract.setBudgetUsd(new BigDecimal("500.00"));
        contract.setStatus(ContractStatus.PENDING_FUNDING);
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
        when(transactions.findByClientUserIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void anAvalancheContractIsRefusedAndNothingIsCreatedOrMutated() {
        contract.setPaymentNetworkId("avalanche-fuji");
        assertThatThrownBy(() -> service.fund(contract.getClientUserId(), contract.getId(), milestone.getId(), "key-1"))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode()).isEqualTo(ErrorCode.FUNDING_INVALID_STATE);
        verify(transactions, never()).saveAndFlush(any());
        verifyNoInteractions(payment);
        assertThat(contract.getPaymentRail()).isNull();
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.PENDING_FUNDING);
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.PENDING_FUNDING);
    }

    @Test
    void aLegacyContractIsNotStoppedByTheAvalancheGuard() {
        for (String networkId : new String[]{null, "", "solana-devnet"}) {
            contract.setPaymentNetworkId(networkId);
            // it passes the guard and the state checks, and stops at the next check: the client has no bank details on file
            when(users.findById(contract.getClientUserId())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.fund(contract.getClientUserId(), contract.getId(), milestone.getId(), "key-1"))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ex -> ((ApplicationException) ex).getErrorCode()).isEqualTo(ErrorCode.FUNDING_PAYMENT_METHOD_INVALID);
        }
        verify(transactions, never()).saveAndFlush(any());
    }
}
