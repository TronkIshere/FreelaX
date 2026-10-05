package com.marketplace.backend.service;

import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.request.funding.FundMilestoneRequest;
import com.marketplace.backend.dto.response.bofa.CheckoutOrderResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FundingServiceTest {
    private FundingTransactionRepository transactions;
    private WorkContractRepository contracts;
    private MilestoneRepository milestones;
    private JobRepository jobs;
    private UserRepository users;
    private PaymentBackendClient payment;
    private FundingService service;
    private WorkContract contract;
    private Milestone milestone;
    private Job job;
    private User client;
    private FundingTransaction saved;

    @BeforeEach
    void setUp() {
        transactions = mock(FundingTransactionRepository.class);
        contracts = mock(WorkContractRepository.class);
        milestones = mock(MilestoneRepository.class);
        jobs = mock(JobRepository.class);
        users = mock(UserRepository.class);
        payment = mock(PaymentBackendClient.class);
        TransactionTemplate tx = new TransactionTemplate(new PlatformTransactionManager() {
            @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
                return mock(TransactionStatus.class);
            }
            @Override public void commit(TransactionStatus status) { }
            @Override public void rollback(TransactionStatus status) { }
        });
        service = new FundingService(transactions, contracts, milestones, jobs, users, payment,
                mock(NotificationService.class), tx);

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
        client = new User();
        client.setId(contract.getClientUserId());
        client.setUserType(UserType.CLIENT);
        client.setBankCode(BankCode.VIETCOMBANK);
        client.setBankAccountNumber("123456789");
        client.setBankAccountHolderName("Client");
        when(contracts.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(milestones.findWithLockById(milestone.getId())).thenReturn(Optional.of(milestone));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(users.findById(client.getId())).thenReturn(Optional.of(client));
        when(transactions.saveAndFlush(any())).thenAnswer(invocation -> {
            saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(transactions.save(any(FundingTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactions.findWithLockById(any())).thenAnswer(invocation -> Optional.ofNullable(saved));
        when(transactions.findById(any())).thenAnswer(invocation -> Optional.ofNullable(saved));
    }

    @Test
    void confirmedCaptureActivatesContractAndReplaysSameKeyWithoutAnotherCharge() {
        CheckoutOrderResult created = order("CREATED");
        CheckoutOrderResult captured = order("CAPTURED");
        captured.setId(created.getId());
        when(payment.createFundingOrder(eq(client.getId()), eq(job.getId()), any(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(created);
        when(payment.captureFundingOrder(created.getId())).thenReturn(captured);

        var first = service.fund(client.getId(), contract.getId(), milestone.getId(), "key-1", request("500.00"));
        when(transactions.findByClientUserIdAndIdempotencyKey(client.getId(), "key-1"))
                .thenReturn(Optional.of(saved));
        var second = service.fund(client.getId(), contract.getId(), milestone.getId(), "key-1", request("500.00"));

        assertThat(first.getFundingStatus()).isEqualTo("SUCCEEDED");
        assertThat(second.getFundingTransactionId()).isEqualTo(first.getFundingTransactionId());
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.FUNDED);
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(job.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
        verify(payment, times(1)).captureFundingOrder(created.getId());
    }

    @Test
    void reusedKeyWithChangedPayloadIsConflict() {
        saved = new FundingTransaction();
        saved.setPayloadHash("different");
        when(transactions.findByClientUserIdAndIdempotencyKey(client.getId(), "key-1"))
                .thenReturn(Optional.of(saved));
        assertThatThrownBy(() -> service.fund(client.getId(), contract.getId(), milestone.getId(),
                "key-1", request("500.00")))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUNDING_KEY_CONFLICT);
        verifyNoInteractions(payment);
    }

    @Test
    void nonOwnerCannotStartFunding() {
        assertThatThrownBy(() -> service.fund(UUID.randomUUID(), contract.getId(), milestone.getId(),
                "key-1", request("500.00")))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUNDING_NOT_FOUND);
        verifyNoInteractions(payment);
    }

    @Test
    void staleAmountIsRejectedBeforeProviderCall() {
        assertThatThrownBy(() -> service.fund(client.getId(), contract.getId(), milestone.getId(),
                "key-1", request("499.00")))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FUNDING_AMOUNT_CHANGED);
        verifyNoInteractions(payment);
    }

    @Test
    void definitiveProviderRejectionFailsWithoutFundingMilestone() {
        when(payment.findFundingOrder(anyString())).thenReturn(null);
        when(payment.createFundingOrder(any(), any(), any(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT));

        var result = service.fund(client.getId(), contract.getId(), milestone.getId(),
                "key-1", request("500.00"));
        assertThat(result.getFundingStatus()).isEqualTo("FAILED");
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.PENDING_FUNDING);
        verify(payment, never()).captureFundingOrder(any());
    }

    @Test
    void providerTimeoutStaysUnknownUntilReconciliationFindsCapturedOrder() {
        when(payment.findFundingOrder(anyString())).thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(order("CAPTURED"));
        var first = service.fund(client.getId(), contract.getId(), milestone.getId(), "key-1", request("500.00"));
        assertThat(first.getFundingStatus()).isEqualTo("UNKNOWN");
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.PENDING_FUNDING);

        when(transactions.findTop50ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(any(), any(LocalDateTime.class)))
                .thenReturn(List.of(saved));
        service.reconcile();
        assertThat(saved.getStatus()).isEqualTo(FundingStatus.SUCCEEDED);
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.FUNDED);
        when(transactions.findByClientUserIdAndIdempotencyKey(client.getId(), "key-1"))
                .thenReturn(Optional.of(saved));
        assertThat(service.fund(client.getId(), contract.getId(), milestone.getId(),
                "key-1", request("500.00")).getFundingStatus()).isEqualTo("SUCCEEDED");
        verify(payment, never()).captureFundingOrder(any());
    }

    @Test
    void captureTimeoutReconcilesKnownOrderWithoutCreatingAnother() {
        CheckoutOrderResult created = order("CREATED");
        CheckoutOrderResult captured = order("CAPTURED");
        captured.setId(created.getId());
        when(payment.createFundingOrder(any(), any(), any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(created);
        when(payment.captureFundingOrder(created.getId())).thenThrow(new ResourceAccessException("timeout"));
        when(payment.getFundingOrder(created.getId())).thenReturn(captured);

        var first = service.fund(client.getId(), contract.getId(), milestone.getId(), "key-2", request("500.00"));
        assertThat(first.getFundingStatus()).isEqualTo("UNKNOWN");
        assertThat(saved.getCheckoutOrderId()).isEqualTo(created.getId());
        when(transactions.findTop50ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(any(), any(LocalDateTime.class)))
                .thenReturn(List.of(saved));

        service.reconcile();

        assertThat(saved.getStatus()).isEqualTo(FundingStatus.SUCCEEDED);
        verify(payment, times(1)).createFundingOrder(any(), any(), any(), anyString(), anyString(), anyString(), anyString());
        verify(payment, times(1)).captureFundingOrder(created.getId());
    }

    private FundMilestoneRequest request(String amount) {
        FundMilestoneRequest request = new FundMilestoneRequest();
        request.setPaymentMethodId("BANK_ACCOUNT_ON_FILE");
        FundMilestoneRequest.ExpectedAmount expected = new FundMilestoneRequest.ExpectedAmount();
        expected.setAmount(new BigDecimal(amount));
        expected.setCurrency("USD");
        request.setExpectedAmount(expected);
        return request;
    }

    private CheckoutOrderResult order(String status) {
        CheckoutOrderResult order = new CheckoutOrderResult();
        order.setId(UUID.randomUUID());
        order.setJobId(job.getId());
        order.setPayerUserId(client.getId());
        order.setAmountUsd(new BigDecimal("500.00"));
        order.setPayerBankAccountNumber(client.getBankAccountNumber());
        order.setStatus(status);
        return order;
    }
}
